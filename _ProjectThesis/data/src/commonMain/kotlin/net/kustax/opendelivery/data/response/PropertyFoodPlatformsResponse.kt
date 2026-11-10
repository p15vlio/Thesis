package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class PropertyFoodPlatformsResponse(
    val platforms: List<String>
)
