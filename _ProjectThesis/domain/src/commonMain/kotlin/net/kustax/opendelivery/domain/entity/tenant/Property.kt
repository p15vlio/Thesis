package net.kustax.opendelivery.domain.entity.tenant


data class Property(
    val id: String,
    val name: String,
    val streetName: String,
    val streetNo: String,
    val postalCode: String,
    val area: String?,
    val level: Int,
    val nameOnDoorbell: String,
    val contactPhone: String,
    val city: String,
    val country: String,
    val timezone: String,
    val notes: String?,
    val latitude: Double?,
    val longitude: Double?,
    // Guest info shown on the kiosk; checkOutTime is "HH:mm"
    val wifiName: String?,
    val wifiPassword: String?,
    val checkOutTime: String?,
    val houseRules: String?,
    val isActive: Boolean,
    val createdAt: Long
)
