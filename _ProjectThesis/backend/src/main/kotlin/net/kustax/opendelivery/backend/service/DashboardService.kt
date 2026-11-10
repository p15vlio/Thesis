package net.kustax.opendelivery.backend.service

import net.kustax.opendelivery.data.response.DashboardSummaryResponse
import net.kustax.opendelivery.domain.repository.DeviceRepository
import net.kustax.opendelivery.domain.repository.OrderRepository
import net.kustax.opendelivery.domain.repository.PropertyRepository

class DashboardService(
    private val propertyRepository: PropertyRepository,
    private val deviceRepository: DeviceRepository,
    private val orderRepository: OrderRepository
) {

    suspend fun getSummary(): DashboardSummaryResponse {
        val properties = propertyRepository.findAll()
        val totalProperties = properties.size
        val activeProperties = properties.count { it.isActive }

        val allDevices = properties.flatMap { deviceRepository.findByPropertyId(it.id) }
        val totalDevices = allDevices.size
        val activeDevices = allDevices.count { it.isActive }

        val activeOrders = properties.flatMap { orderRepository.findByPropertyId(it.id) }
            .filter { it.status.name in setOf("PENDING", "CONFIRMED", "PREPARING", "OUT_FOR_DELIVERY") }

        return DashboardSummaryResponse(
            totalProperties = totalProperties,
            activeProperties = activeProperties,
            totalDevices = totalDevices,
            activeDevices = activeDevices,
            pendingOrders = activeOrders.count { it.status.name == "PENDING" },
            confirmedOrders = activeOrders.count { it.status.name == "CONFIRMED" },
            preparingOrders = activeOrders.count { it.status.name == "PREPARING" },
            outForDeliveryOrders = activeOrders.count { it.status.name == "OUT_FOR_DELIVERY" }
        )
    }
}
