package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val role: String,
    val expiresAt: Long
)
