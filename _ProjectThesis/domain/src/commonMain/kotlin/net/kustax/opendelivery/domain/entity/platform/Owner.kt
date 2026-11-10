package net.kustax.opendelivery.domain.entity.platform

data class Owner(
    val id: String,
    val companyName: String,
    val distinctiveTitle: String?,
    val vatId: String,
    val gemhNumber: String?,
    val legalForm: String?,
    val companyActivity: String?,
    val streetName: String,
    val streetNo: String?,
    val postalCode: String,
    val city: String,
    val contactFirstName: String,
    val contactLastName: String,
    val contactEmail: String,
    val contactPhone: String,
    val secondaryContactName: String?,
    val secondaryContactPhone: String?,
    val secondaryContactEmail: String?,
    val website: String?,
    val notes: String?,
    val schemaName: String,
    val isActive: Boolean,
    val createdAt: Long
)
