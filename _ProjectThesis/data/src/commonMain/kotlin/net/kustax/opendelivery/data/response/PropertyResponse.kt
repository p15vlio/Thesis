package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class PropertyResponse(
    val id: String,
    val name: String,
    val streetName: String,
    val streetNo: String,
    val postalCode: String,
    val area: String? = null,
    val level: Int,
    val nameOnDoorbell: String,
    val contactPhone: String,
    val city: String,
    val country: String,
    val timezone: String,
    val notes: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val wifiName: String? = null,
    val wifiPassword: String? = null,
    // "HH:mm"
    val checkOutTime: String? = null,
    val houseRules: String? = null,
    val isActive: Boolean,
    val createdAt: Long
)
