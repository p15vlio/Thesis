package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.tenant.OrderItem

interface OrderItemRepository {
    suspend fun insert(item: OrderItem)
    suspend fun findByOrderId(orderId: String): List<OrderItem>
}
