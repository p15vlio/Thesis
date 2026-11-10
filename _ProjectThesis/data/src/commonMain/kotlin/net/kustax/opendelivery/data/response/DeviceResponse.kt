package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class DeviceResponse(
    val id: String,
    val propertyId: String,
    val name: String,
    val androidDeviceId: String?,
    val isActive: Boolean,
    val isKioskEnabled: Boolean,
    val lastSeenAt: Long?,
    val activationCode: String? = null,
    val activatedAt: Long? = null,
    val qrCodeBase64: String? = null,
    val token: String? = null
)
