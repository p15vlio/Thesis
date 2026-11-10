package net.kustax.opendelivery.domain.entity.tenant

data class Device(
    val id: String,
    val propertyId: String,
    val name: String,
    val androidDeviceId: String?,
    val isActive: Boolean,
    val isKioskEnabled: Boolean,
    val lastSeenAt: Long?,
    val activationCode: String?,
    val activatedAt: Long?
)
