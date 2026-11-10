package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class GemhSettingsResponse(
    val apiKeySet: Boolean
)
