package net.kustax.opendelivery.domain.entity.tenant

import net.kustax.opendelivery.domain.enum.ExternalPlatform
import net.kustax.opendelivery.domain.enum.OrderStatus
import net.kustax.opendelivery.domain.enum.OrderType

// externalPlatform .. estimatedDeliveryAt are only set for FOOD_DELIVERY orders. Their items reference
// the platform's menu item ids (OrderItem.productId) and totalAmount includes deliveryFee.
data class Order(
    val id: String,
    val deviceId: String,
    val propertyId: String,
    val guestEmail: String?,
    val status: OrderStatus,
    val type: OrderType,
    val externalPlatform: ExternalPlatform?,
    val externalOrderId: String?,
    val restaurantId: String?,
    val restaurantName: String?,
    val deliveryFee: Long,
    val estimatedDeliveryAt: Long?,
    val totalAmount: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
