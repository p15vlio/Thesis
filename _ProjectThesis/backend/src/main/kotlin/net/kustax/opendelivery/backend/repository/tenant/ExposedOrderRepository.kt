package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.OrdersTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.entity.tenant.Order
import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.enum.OrderType
import net.kustax.opendelivery.domain.repository.OrderRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedOrderRepository : OrderRepository {

    override suspend fun create(order: Order): Order = tenantQuery {
        OrdersTable.insert {
            it[id] = order.id
            it[deviceId] = order.deviceId
            it[propertyId] = order.propertyId
            it[guestEmail] = order.guestEmail
            it[status] = order.status.name
            it[type] = order.type.name
            it[externalPlatform] = order.externalPlatform?.name
            it[externalOrderId] = order.externalOrderId
            it[restaurantId] = order.restaurantId
            it[restaurantName] = order.restaurantName
            it[deliveryFee] = order.deliveryFee
            it[estimatedDeliveryAt] = order.estimatedDeliveryAt
            it[totalAmount] = order.totalAmount
            it[notes] = order.notes
            it[createdAt] = order.createdAt
            it[updatedAt] = order.updatedAt
        }
        order
    }

    override suspend fun findById(id: String): Order? = tenantQuery {
        OrdersTable.selectAll()
            .where { OrdersTable.id eq id }
            .singleOrNull()
            ?.toOrder()
    }

    override suspend fun findByIdForUpdate(id: String): Order? = tenantQuery {
        OrdersTable.selectAll()
            .where { OrdersTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.toOrder()
    }

    override suspend fun findByPropertyId(propertyId: String): List<Order> = tenantQuery {
        OrdersTable.selectAll()
            .where { OrdersTable.propertyId eq propertyId }
            .map { it.toOrder() }
    }

    override suspend fun findByDeviceId(deviceId: String): List<Order> = tenantQuery {
        OrdersTable.selectAll()
            .where { OrdersTable.deviceId eq deviceId }
            .map { it.toOrder() }
    }

    override suspend fun findByStatus(status: OrderStatus): List<Order> = tenantQuery {
        OrdersTable.selectAll()
            .where { OrdersTable.status eq status.name }
            .map { it.toOrder() }
    }

    override suspend fun findInFlightFoodOrders(): List<Order> = tenantQuery {
        OrdersTable.selectAll()
            .where {
                (OrdersTable.type eq OrderType.FOOD_DELIVERY.name) and
                    OrdersTable.externalOrderId.isNotNull() and
                    (OrdersTable.status notInList listOf(OrderStatus.DELIVERED.name, OrderStatus.CANCELLED.name))
            }
            .map { it.toOrder() }
    }

    override suspend fun countByStatus(status: OrderStatus): Long = tenantQuery {
        OrdersTable.selectAll()
            .where { OrdersTable.status eq status.name }
            .count()
    }

    override suspend fun updateStatus(id: String, status: OrderStatus, expectedStatus: OrderStatus): Order? = tenantQuery {
        val now = System.currentTimeMillis()
        val updated = OrdersTable.update({ (OrdersTable.id eq id) and (OrdersTable.status eq expectedStatus.name) }) {
            it[OrdersTable.status] = status.name
            it[updatedAt] = now
        }
        if (updated == 0) return@tenantQuery null
        OrdersTable.selectAll()
            .where { OrdersTable.id eq id }
            .single()
            .toOrder()
    }

    // The status condition is part of the UPDATE, so a cancel that commits first always wins: the id is then not
    // recorded and the caller takes the platform order back. Any non-cancelled status is accepted because the
    // platform's first status event (PREPARING) can arrive before its placeOrder response has been recorded.
    override suspend fun assignExternalOrder(id: String, externalOrderId: String): Boolean = tenantQuery {
        OrdersTable.update({ (OrdersTable.id eq id) and (OrdersTable.status neq OrderStatus.CANCELLED.name) }) {
            it[OrdersTable.externalOrderId] = externalOrderId
        } == 1
    }

    override suspend fun updateEstimatedDelivery(id: String, estimatedDeliveryAt: Long?): Unit = tenantQuery {
        OrdersTable.update({ OrdersTable.id eq id }) {
            it[OrdersTable.estimatedDeliveryAt] = estimatedDeliveryAt
        }
    }
}

private fun ResultRow.toOrder() = Order(
    id = this[OrdersTable.id],
    deviceId = this[OrdersTable.deviceId],
    propertyId = this[OrdersTable.propertyId],
    guestEmail = this[OrdersTable.guestEmail],
    status = enumValueOf<OrderStatus>(this[OrdersTable.status]),
    type = enumValueOf<OrderType>(this[OrdersTable.type]),
    externalPlatform = this[OrdersTable.externalPlatform]?.let { enumValueOf<ExternalPlatform>(it) },
    externalOrderId = this[OrdersTable.externalOrderId],
    restaurantId = this[OrdersTable.restaurantId],
    restaurantName = this[OrdersTable.restaurantName],
    deliveryFee = this[OrdersTable.deliveryFee],
    estimatedDeliveryAt = this[OrdersTable.estimatedDeliveryAt],
    totalAmount = this[OrdersTable.totalAmount],
    notes = this[OrdersTable.notes],
    createdAt = this[OrdersTable.createdAt],
    updatedAt = this[OrdersTable.updatedAt]
)
