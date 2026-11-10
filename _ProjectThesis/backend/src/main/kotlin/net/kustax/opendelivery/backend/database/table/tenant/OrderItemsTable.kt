package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// No foreign key on product_id on purpose: food delivery items carry the delivery platform's menu item ids, and
// product_name / unit_price are snapshots, so an order line must outlive (and need not match) a catalog product.
object OrderItemsTable : Table("order_items") {
    val id = varchar("id", 36)
    val orderId = varchar("order_id", 36).references(OrdersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val productId = varchar("product_id", 36)
    val productName = varchar("product_name", 255)
    val unitPrice = long("unit_price")
    val quantity = integer("quantity")
    val subtotal = long("subtotal")

    override val primaryKey = PrimaryKey(id)
}
