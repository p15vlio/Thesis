package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class ActivateDeviceRequest(
    val activationCode: String,
    val androidDeviceId: String
)
