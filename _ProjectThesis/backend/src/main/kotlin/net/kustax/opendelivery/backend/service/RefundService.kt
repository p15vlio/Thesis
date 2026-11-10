package net.kustax.opendelivery.backend.service

import kotlinx.coroutines.CancellationException
import net.kustax.opendelivery.domain.entity.tenant.Payment
import net.kustax.opendelivery.domain.enum.PaymentStatus
import net.kustax.opendelivery.domain.port.PaymentGateway
import net.kustax.opendelivery.domain.repository.PaymentRepository
import org.slf4j.LoggerFactory

// Returns the money of an order that was paid and then cancelled. Shared by PaymentService (the delivery
// platform rejected a paid food order) and OrderService (staff or the kiosk cancel a paid order).
class RefundService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val auditLogService: AuditLogService
) {

    private val logger = LoggerFactory.getLogger(RefundService::class.java)

    // Never throws: a failed refund is logged and audited as needing a manual refund. Returns whether this call
    // refunded the payment through the gateway.
    //
    // Invariant: a payment reaches the gateway's refund at most once. The payment is first claimed with a
    // compare-and-set COMPLETED -> REFUNDED, and only the caller whose claim changed the row calls the gateway;
    // anyone holding a stale COMPLETED copy of the same payment loses the claim and refunds nothing.
    suspend fun refund(payment: Payment, actorId: String, actorRole: String, action: String, schemaName: String): Boolean {
        // null: the claim itself failed (database error), so the payment is still COMPLETED and needs a manual refund
        val claimed: Boolean? = try {
            paymentRepository.updateStatus(payment.id, PaymentStatus.REFUNDED, expectedStatus = PaymentStatus.COMPLETED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Could not claim payment {} for a refund", payment.id, e)
            null
        }
        if (claimed == false) {
            logger.info("Payment {} of order {} is no longer COMPLETED (already refunded); not refunding it again", payment.id, payment.orderId)
            return false
        }

        val refunded = claimed == true && try {
            payment.providerTransactionId?.let { paymentGateway.refund(it, payment.amount) } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Refund of payment {} for order {} failed", payment.id, payment.orderId, e)
            false
        }
        // The gateway did not return the money: put the claim back so the record says the payment still stands
        if (claimed == true && !refunded) {
            runCatching { paymentRepository.updateStatus(payment.id, PaymentStatus.COMPLETED, expectedStatus = PaymentStatus.REFUNDED) }
                .onFailure { logger.error("Could not reset payment {} after its refund failed", payment.id, it) }
        }

        val outcome = if (refunded) "refunded" else "REFUND FAILED - manual refund required"
        runCatching {
            auditLogService.log(
                actorId, actorRole, action, "Payment", payment.id, schemaName,
                "order=${payment.orderId}, amount=${payment.amount}, transaction=${payment.providerTransactionId}, $outcome"
            )
        }.onFailure { logger.error("Could not write audit entry for payment {}", payment.id, it) }
        logger.warn("Payment {} of cancelled order {}: {}", payment.id, payment.orderId, outcome)
        return refunded
    }
}
