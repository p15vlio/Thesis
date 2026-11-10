package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateOwnerRequest(
    val companyName: String,
    val distinctiveTitle: String? = null,
    val gemhNumber: String? = null,
    val legalForm: String? = null,
    val companyActivity: String? = null,
    val streetName: String,
    val streetNo: String? = null,
    val postalCode: String,
    val city: String,
    val contactFirstName: String,
    val contactLastName: String,
    val contactPhone: String,
    val secondaryContactName: String? = null,
    val secondaryContactPhone: String? = null,
    val secondaryContactEmail: String? = null,
    val website: String? = null,
    val notes: String? = null
)
