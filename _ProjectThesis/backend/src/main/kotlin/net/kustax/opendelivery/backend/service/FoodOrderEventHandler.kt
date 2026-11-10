package net.kustax.opendelivery.backend.service

import kotlinx.coroutines.withContext
import net.kustax.opendelivery.backend.database.TenantContext
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.port.FoodOrderEventListener
import net.kustax.opendelivery.domain.repository.OrderRepository
import org.slf4j.LoggerFactory

// Receives the status events a food delivery platform sends for orders it accepted. Platform events
// arrive outside any HTTP request, so the tenant is re-established from the schema name they carry.
class FoodOrderEventHandler(private val orderRepository: OrderRepository) : FoodOrderEventListener {

    private val logger = LoggerFactory.getLogger(FoodOrderEventHandler::class.java)

    override suspend fun onStatusChanged(schemaName: String, orderId: String, newStatus: OrderStatus, etaMinutes: Int?) {
        withContext(TenantContext(schemaName)) {
            tenantQuery {
                val order = orderRepository.findById(orderId) ?: return@tenantQuery
                // A cancelled or delivered order is final, and a late or duplicate event must never move
                // an order backwards.
                if (order.status == OrderStatus.DELIVERED || order.status == OrderStatus.CANCELLED) return@tenantQuery
                if (newStatus.ordinal <= order.status.ordinal) return@tenantQuery
                // Compare-and-set: if the order was cancelled (or otherwise moved) since it was read above,
                // the event is stale and is dropped.
                if (orderRepository.updateStatus(orderId, newStatus, expectedStatus = order.status) == null) {
                    logger.info("Ignored {} event for order {}: it is no longer {}", newStatus, orderId, order.status)
                    return@tenantQuery
                }
                orderRepository.updateEstimatedDelivery(orderId, etaMinutes?.let { System.currentTimeMillis() + it * 60_000L })
            }
        }
    }
}
