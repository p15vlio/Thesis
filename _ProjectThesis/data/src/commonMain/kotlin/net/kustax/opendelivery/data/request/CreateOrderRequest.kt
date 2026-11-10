package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

// platform and restaurantId are required for FOOD_DELIVERY orders, whose items reference the
// platform's menu item ids in OrderItemRequest.productId
@Serializable
data class CreateOrderRequest(
    val guestEmail: String?,
    val type: String,
    val items: List<OrderItemRequest>,
    val notes: String?,
    val platform: String? = null,
    val restaurantId: String? = null
)
