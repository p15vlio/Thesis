package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class FoodRestaurantResponse(
    val platform: String,
    val id: String,
    val name: String,
    val cuisine: String,
    val rating: Double,
    val deliveryFeeCents: Long,
    val etaMinutes: Int,
    val minOrderCents: Long? = null
)
