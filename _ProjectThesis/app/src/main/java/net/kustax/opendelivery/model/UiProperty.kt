package net.kustax.opendelivery.model

// Guest information the owner has not filled in is null
data class UiProperty(
    val id: String,
    val name: String,
    val address: String,
    val contactPhone: String,
    val wifiName: String?,
    val wifiPassword: String?,
    val checkOutTime: String?,
    val houseRules: String?
)

// Real Greek emergency services, dialed by the guest from the Info screen
data class EmergencyService(val label: String, val number: String)

val EMERGENCY_SERVICES = listOf(
    EmergencyService("European emergency", "112"),
    EmergencyService("Police", "100"),
    EmergencyService("Ambulance", "166"),
    EmergencyService("Fire Service", "199")
)
