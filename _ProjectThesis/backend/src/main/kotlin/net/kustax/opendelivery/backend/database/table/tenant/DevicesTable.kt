package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object DevicesTable : Table("devices") {
    val id = varchar("id", 36)
    val propertyId = varchar("property_id", 36).references(PropertiesTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val name = varchar("name", 255)
    val androidDeviceId = varchar("android_device_id", 255).nullable()
    val isActive = bool("is_active")
    val isKioskEnabled = bool("is_kiosk_enabled")
    val lastSeenAt = long("last_seen_at").nullable()
    val activationCode = varchar("activation_code", 16).nullable()
    val activatedAt = long("activated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
