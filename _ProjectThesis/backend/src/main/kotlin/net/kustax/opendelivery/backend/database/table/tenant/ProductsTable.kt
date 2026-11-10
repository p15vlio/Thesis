package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.Table

object ProductsTable : Table("products") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val description = varchar("description", 1024)
    val category = varchar("category", 32)
    val basePrice = long("base_price")
    val imageUrl = varchar("image_url", 512).nullable()
    val isAvailable = bool("is_available")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
