package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class ConnectFoodPlatformRequest(
    val apiKey: String
)
