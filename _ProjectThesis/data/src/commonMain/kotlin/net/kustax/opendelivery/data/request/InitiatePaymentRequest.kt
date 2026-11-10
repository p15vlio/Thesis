package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

// entryMode is a CardEntryMode name. pinVerified is only sent for CARD_CHIP (the PIN itself never leaves
// the terminal); cardLast4 is only sent when the reader exposes the card number (magnetic stripe).
@Serializable
data class InitiatePaymentRequest(
    val orderId: String,
    val entryMode: String,
    val pinVerified: Boolean? = null,
    val cardLast4: String? = null
)
