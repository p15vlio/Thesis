package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// Row presence means "disabled at this property" — no boolean column. Toggling on deletes the row,
// toggling off inserts it.
object ProductPropertyOverridesTable : Table("product_property_overrides") {
    val productId = varchar("product_id", 36).references(ProductsTable.id, onDelete = ReferenceOption.CASCADE)
    val propertyId = varchar("property_id", 36).references(PropertiesTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(productId, propertyId)
}
