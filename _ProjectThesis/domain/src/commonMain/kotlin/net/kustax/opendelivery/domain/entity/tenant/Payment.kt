package net.kustax.opendelivery.domain.entity.tenant

import net.kustax.opendelivery.domain.enum.CardEntryMode
import net.kustax.opendelivery.domain.enum.PaymentStatus

// One card payment attempt: an order can have several FAILED attempts and at most one COMPLETED
data class Payment(
    val id: String,
    val orderId: String,
    val entryMode: CardEntryMode,
    val cardLast4: String?,
    val status: PaymentStatus,
    val declineReason: String?,
    val amount: Long,
    val providerTransactionId: String?,
    val createdAt: Long
)
