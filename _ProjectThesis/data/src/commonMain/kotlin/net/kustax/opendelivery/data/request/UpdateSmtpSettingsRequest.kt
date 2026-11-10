package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateSmtpSettingsRequest(
    val host: String,
    val port: Int,
    val security: String,
    val username: String,
    // null keeps the stored password — the client never receives it back, so it can't resend it.
    val password: String? = null,
    val fromAddress: String,
    val fromName: String
)
