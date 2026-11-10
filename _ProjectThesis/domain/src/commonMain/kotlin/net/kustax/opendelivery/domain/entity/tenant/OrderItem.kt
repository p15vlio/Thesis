package net.kustax.opendelivery.domain.entity.tenant

// productName and unitPrice are snapshots captured at order time
data class OrderItem(
    val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val subtotal: Long
)
