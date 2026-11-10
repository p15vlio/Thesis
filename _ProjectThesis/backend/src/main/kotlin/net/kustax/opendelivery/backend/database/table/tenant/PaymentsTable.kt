package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object PaymentsTable : Table("payments") {
    val id = varchar("id", 36)
    val orderId = varchar("order_id", 36).references(OrdersTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val entryMode = varchar("entry_mode", 32)
    val cardLast4 = varchar("card_last4", 4).nullable()
    val status = varchar("status", 32)
    val declineReason = varchar("decline_reason", 255).nullable()
    val amount = long("amount")
    val providerTransactionId = varchar("provider_transaction_id", 255).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
