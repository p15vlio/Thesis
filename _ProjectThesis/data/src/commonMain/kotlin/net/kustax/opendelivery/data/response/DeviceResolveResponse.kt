package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class DeviceResolveResponse(
    val deviceId: String,
    val schemaName: String
)
