package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

// orderStatus and message are only set when the outcome needs explaining to the guest: an approved payment
// whose order could not be handed to its delivery platform comes back with orderStatus CANCELLED and a
// message saying the card is being refunded.
@Serializable
data class PaymentResponse(
    val id: String,
    val orderId: String,
    val entryMode: String,
    val cardLast4: String?,
    val status: String,
    val declineReason: String?,
    val amount: Long,
    val providerTransactionId: String?,
    val createdAt: Long,
    val orderStatus: String? = null,
    val message: String? = null
)
