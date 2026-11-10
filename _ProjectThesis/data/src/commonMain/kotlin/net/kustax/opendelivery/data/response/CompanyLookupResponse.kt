package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class CompanyLookupResponse(
    val vatId: String,
    val gemhNumber: String? = null,
    val companyName: String,
    val distinctiveTitle: String? = null,
    val legalForm: String? = null,
    val companyActivity: String? = null,
    val streetName: String? = null,
    val streetNo: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val website: String? = null,
    val statusDescription: String? = null,
    val isActive: Boolean
)
