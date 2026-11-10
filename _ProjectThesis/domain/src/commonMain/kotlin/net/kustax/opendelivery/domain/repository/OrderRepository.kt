package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.tenant.Order
import net.kustax.opendelivery.domain.enum.OrderStatus

interface OrderRepository {
    suspend fun create(order: Order): Order
    suspend fun findById(id: String): Order?
    // Takes a row lock held until the surrounding transaction ends; concurrent callers for the same order queue up
    suspend fun findByIdForUpdate(id: String): Order?
    suspend fun findByPropertyId(propertyId: String): List<Order>
    suspend fun findByDeviceId(deviceId: String): List<Order>
    suspend fun findByStatus(status: OrderStatus): List<Order>
    // FOOD_DELIVERY orders already placed with their platform that have not reached a final status
    suspend fun findInFlightFoodOrders(): List<Order>
    suspend fun countByStatus(status: OrderStatus): Long
    // Compare-and-set: only applies when the order is still in expectedStatus. Returns the updated order, or
    // null when the order was missing or had already moved on (nothing is written then).
    suspend fun updateStatus(id: String, status: OrderStatus, expectedStatus: OrderStatus): Order?
    // Records the platform's order id unless the order has been cancelled meanwhile. Returns whether it was recorded.
    suspend fun assignExternalOrder(id: String, externalOrderId: String): Boolean
    suspend fun updateEstimatedDelivery(id: String, estimatedDeliveryAt: Long?)
}
