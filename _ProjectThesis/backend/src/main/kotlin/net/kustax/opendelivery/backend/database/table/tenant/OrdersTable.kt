package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object OrdersTable : Table("orders") {
    val id = varchar("id", 36)
    val deviceId = varchar("device_id", 36).references(DevicesTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val propertyId = varchar("property_id", 36).references(PropertiesTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val guestEmail = varchar("guest_email", 255).nullable()
    val status = varchar("status", 32).index()
    val type = varchar("type", 32)
    val externalPlatform = varchar("external_platform", 32).nullable()
    val externalOrderId = varchar("external_order_id", 64).nullable()
    val restaurantId = varchar("restaurant_id", 64).nullable()
    val restaurantName = varchar("restaurant_name", 255).nullable()
    val deliveryFee = long("delivery_fee").default(0)
    val estimatedDeliveryAt = long("estimated_delivery_at").nullable()
    val totalAmount = long("total_amount")
    val notes = varchar("notes", 1024).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
