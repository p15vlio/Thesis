package net.kustax.opendelivery.domain.repository

import net.kustax.opendelivery.domain.entity.tenant.Payment
import net.kustax.opendelivery.domain.enum.PaymentStatus

interface PaymentRepository {
    suspend fun create(payment: Payment): Payment
    // Compare-and-set: only applies when the payment is still in expectedStatus. Returns whether a row changed.
    suspend fun updateStatus(id: String, status: PaymentStatus, expectedStatus: PaymentStatus): Boolean
    // All attempts for the order, oldest first
    suspend fun findByOrderId(orderId: String): List<Payment>
}
