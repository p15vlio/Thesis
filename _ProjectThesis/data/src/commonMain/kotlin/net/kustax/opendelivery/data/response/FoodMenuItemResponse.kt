package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class FoodMenuItemResponse(
    val id: String,
    val restaurantId: String,
    val name: String,
    val description: String,
    val priceCents: Long,
    val category: String
)
