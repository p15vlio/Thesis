package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class OrderResponse(
    val id: String,
    val deviceId: String,
    val propertyId: String,
    val guestEmail: String?,
    val status: String,
    val type: String,
    val externalPlatform: String?,
    val externalOrderId: String? = null,
    val restaurantId: String? = null,
    val restaurantName: String? = null,
    val deliveryFee: Long = 0,
    val estimatedDeliveryAt: Long? = null,
    val totalAmount: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<OrderItemResponse>
)
