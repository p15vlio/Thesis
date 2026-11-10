package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class OrderItemRequest(
    val productId: String,
    val quantity: Int
)
