package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePropertyRequest(
    val name: String,
    val streetName: String,
    val streetNo: String,
    val postalCode: String,
    val area: String? = null,
    val level: Int = 0,
    val nameOnDoorbell: String,
    val contactPhone: String,
    val city: String,
    val notes: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val wifiName: String? = null,
    val wifiPassword: String? = null,
    // "HH:mm"
    val checkOutTime: String? = null,
    val houseRules: String? = null
)
