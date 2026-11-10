package net.kustax.opendelivery.domain.entity.platform

data class CompanyRegistryRecord(
    val vatId: String,
    val gemhNumber: String?,
    val companyName: String,
    val distinctiveTitle: String?,
    val legalForm: String?,
    val companyActivity: String?,
    val streetName: String?,
    val streetNo: String?,
    val postalCode: String?,
    val city: String?,
    val website: String?,
    val statusDescription: String?,
    val isActive: Boolean
)
