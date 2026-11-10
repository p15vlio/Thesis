package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class SendTestEmailRequest(val toEmail: String)
