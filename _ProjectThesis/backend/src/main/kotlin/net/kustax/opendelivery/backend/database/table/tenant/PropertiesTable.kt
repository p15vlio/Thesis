package net.kustax.opendelivery.backend.database.table.tenant

import org.jetbrains.exposed.v1.core.Table

object PropertiesTable : Table("properties") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val streetName = varchar("street_name", 255)
    val streetNo = varchar("street_no", 32)
    val postalCode = varchar("postal_code", 16)
    val area = varchar("area", 128).nullable()
    val level = integer("level").default(0)
    val nameOnDoorbell = varchar("name_on_doorbell", 128)
    val contactPhone = varchar("contact_phone", 64)
    val city = varchar("city", 128)
    val country = varchar("country", 128)
    val timezone = varchar("timezone", 64)
    val notes = text("notes").nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val wifiName = varchar("wifi_name", 128).nullable()
    val wifiPassword = varchar("wifi_password", 128).nullable()
    val checkOutTime = varchar("check_out_time", 5).nullable()
    val houseRules = text("house_rules").nullable()
    val isActive = bool("is_active")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
