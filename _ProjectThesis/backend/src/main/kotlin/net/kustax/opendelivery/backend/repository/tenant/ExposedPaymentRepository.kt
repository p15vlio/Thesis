package net.kustax.opendelivery.backend.repository.tenant

import net.kustax.opendelivery.backend.database.table.tenant.PaymentsTable
import net.kustax.opendelivery.backend.repository.tenantQuery
import net.kustax.opendelivery.domain.entity.tenant.Payment
import net.kustax.opendelivery.domain.enum.CardEntryMode
import net.kustax.opendelivery.domain.enum.PaymentStatus
import net.kustax.opendelivery.domain.repository.PaymentRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

class ExposedPaymentRepository : PaymentRepository {

    override suspend fun create(payment: Payment): Payment = tenantQuery {
        PaymentsTable.insert {
            it[id] = payment.id
            it[orderId] = payment.orderId
            it[entryMode] = payment.entryMode.name
            it[cardLast4] = payment.cardLast4
            it[status] = payment.status.name
            it[declineReason] = payment.declineReason
            it[amount] = payment.amount
            it[providerTransactionId] = payment.providerTransactionId
            it[createdAt] = payment.createdAt
        }
        payment
    }

    override suspend fun updateStatus(id: String, status: PaymentStatus, expectedStatus: PaymentStatus): Boolean = tenantQuery {
        PaymentsTable.update({ (PaymentsTable.id eq id) and (PaymentsTable.status eq expectedStatus.name) }) {
            it[PaymentsTable.status] = status.name
        } == 1
    }

    override suspend fun findByOrderId(orderId: String): List<Payment> = tenantQuery {
        PaymentsTable.selectAll()
            .where { PaymentsTable.orderId eq orderId }
            .orderBy(PaymentsTable.createdAt)
            .map { it.toPayment() }
    }
}

private fun ResultRow.toPayment() = Payment(
    id = this[PaymentsTable.id],
    orderId = this[PaymentsTable.orderId],
    entryMode = enumValueOf<CardEntryMode>(this[PaymentsTable.entryMode]),
    cardLast4 = this[PaymentsTable.cardLast4],
    status = enumValueOf<PaymentStatus>(this[PaymentsTable.status]),
    declineReason = this[PaymentsTable.declineReason],
    amount = this[PaymentsTable.amount],
    providerTransactionId = this[PaymentsTable.providerTransactionId],
    createdAt = this[PaymentsTable.createdAt]
)
