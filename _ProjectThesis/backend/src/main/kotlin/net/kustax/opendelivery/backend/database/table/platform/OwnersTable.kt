package net.kustax.opendelivery.backend.database.table.platform

import org.jetbrains.exposed.v1.core.Table

object OwnersTable : Table("owners") {
    val id = varchar("id", 36)
    val companyName = varchar("company_name", 255)
    val distinctiveTitle = varchar("distinctive_title", 255).nullable()
    val vatId = varchar("vat_id", 64)
    val gemhNumber = varchar("gemh_number", 64).nullable()
    val legalForm = varchar("legal_form", 128).nullable()
    val companyActivity = varchar("company_activity", 255).nullable()
    val streetName = varchar("street_name", 255)
    val streetNo = varchar("street_no", 32).nullable()
    val postalCode = varchar("postal_code", 16)
    val city = varchar("city", 128)
    val contactFirstName = varchar("contact_first_name", 128)
    val contactLastName = varchar("contact_last_name", 128)
    val contactEmail = varchar("contact_email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val contactPhone = varchar("contact_phone", 64)
    val secondaryContactName = varchar("secondary_contact_name", 255).nullable()
    val secondaryContactPhone = varchar("secondary_contact_phone", 64).nullable()
    val secondaryContactEmail = varchar("secondary_contact_email", 255).nullable()
    val website = varchar("website", 255).nullable()
    val notes = text("notes").nullable()
    val tenantSchemaName = varchar("schema_name", 128)
    val isActive = bool("is_active")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
