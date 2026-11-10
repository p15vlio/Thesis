package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class  OrderItemResponse(
    val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val subtotal: Long
)
