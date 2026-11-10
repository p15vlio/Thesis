package net.kustax.opendelivery.domain.port

import net.kustax.opendelivery.domain.enum.CardEntryMode

// Outcome of a card authorization: approved with a transaction id, or declined with a reason
data class PaymentAuthorization(
    val approved: Boolean,
    val transactionId: String?,
    val declineReason: String?
)

// Card acquirer contract; the emulated adapter is the only implementation
interface PaymentGateway {
    // pinVerified is only meaningful for CARD_CHIP: the terminal's PIN check outcome (the PIN itself never leaves the device)
    suspend fun initiatePayment(orderId: String, amount: Long, entryMode: CardEntryMode, pinVerified: Boolean?): PaymentAuthorization
    // Returns true when the acquirer accepted the refund of a previously approved transaction
    suspend fun refund(transactionId: String, amount: Long): Boolean
}
