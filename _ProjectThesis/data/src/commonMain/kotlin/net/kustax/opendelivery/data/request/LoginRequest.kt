package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val role: String,
    val schemaName: String? = null
)
