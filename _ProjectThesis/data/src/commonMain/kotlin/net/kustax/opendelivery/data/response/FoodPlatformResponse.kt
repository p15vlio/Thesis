package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

// The API key is never part of a response
@Serializable
data class FoodPlatformResponse(
    val platform: String,
    val connected: Boolean,
    val connectedAt: Long? = null
)
