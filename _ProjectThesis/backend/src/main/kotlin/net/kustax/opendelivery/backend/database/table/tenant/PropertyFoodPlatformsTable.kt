package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// Row presence means "platform is offered at this property" (the opposite of ProductPropertyOverridesTable).
object PropertyFoodPlatformsTable : Table("property_food_platforms") {
    val propertyId = varchar("property_id", 36).references(PropertiesTable.id, onDelete = ReferenceOption.CASCADE)
    val platform = varchar("platform", 32)

    override val primaryKey = PrimaryKey(propertyId, platform)
}
