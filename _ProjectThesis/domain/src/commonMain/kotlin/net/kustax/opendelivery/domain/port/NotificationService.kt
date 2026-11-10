package net.kustax.opendelivery.domain.port

import net.kustax.opendelivery.domain.entity.tenant.OrderItem

interface NotificationService {
    // amount is the order total, which already includes deliveryFee
    suspend fun sendReceipt(toEmail: String, orderId: String, amount: Long, deliveryFee: Long, items: List<OrderItem>)
    suspend fun sendOwnerWelcome(toEmail: String, temporaryPassword: String, loginUrl: String)
    suspend fun sendPasswordReset(toEmail: String, temporaryPassword: String, loginUrl: String)
}
