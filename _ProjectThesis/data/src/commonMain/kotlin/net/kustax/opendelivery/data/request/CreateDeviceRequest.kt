package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateDeviceRequest(
    val name: String,
    val androidDeviceId: String? = null
)
