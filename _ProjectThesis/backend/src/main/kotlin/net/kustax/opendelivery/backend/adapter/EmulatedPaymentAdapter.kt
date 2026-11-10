package net.kustax.opendelivery.backend.adapter

import net.kustax.opendelivery.domain.enum.CardEntryMode
import net.kustax.opendelivery.domain.port.PaymentAuthorization
import net.kustax.opendelivery.domain.port.PaymentGateway
import org.slf4j.LoggerFactory
import java.util.UUID

// Emulated acquirer: contactless always approves, chip approves only after a successful PIN check,
// magnetic stripe is never accepted
class EmulatedPaymentAdapter : PaymentGateway {

    private val logger = LoggerFactory.getLogger(EmulatedPaymentAdapter::class.java)

    override suspend fun initiatePayment(
        orderId: String,
        amount: Long,
        entryMode: CardEntryMode,
        pinVerified: Boolean?
    ): PaymentAuthorization {
        val declineReason = when (entryMode) {
            CardEntryMode.CARD_CONTACTLESS -> null
            CardEntryMode.CARD_CHIP -> if (pinVerified == true) null else "Incorrect PIN"
            CardEntryMode.CARD_MAGSTRIPE -> "Magnetic stripe not accepted"
        }
        logger.info("Emulated payment for order $orderId amount $amount entryMode $entryMode: ${declineReason ?: "approved"}")
        return if (declineReason == null) {
            PaymentAuthorization(approved = true, transactionId = "EMULATED_TXN_${UUID.randomUUID()}", declineReason = null)
        } else {
            PaymentAuthorization(approved = false, transactionId = null, declineReason = declineReason)
        }
    }

    override suspend fun refund(transactionId: String, amount: Long): Boolean {
        logger.info("Emulated refund for transaction $transactionId amount $amount")
        return true
    }
}
