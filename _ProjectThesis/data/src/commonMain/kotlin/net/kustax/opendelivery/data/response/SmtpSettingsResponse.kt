package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class SmtpSettingsResponse(
    val host: String,
    val port: Int,
    val security: String,
    val username: String,
    val passwordSet: Boolean,
    val fromAddress: String,
    val fromName: String
)
