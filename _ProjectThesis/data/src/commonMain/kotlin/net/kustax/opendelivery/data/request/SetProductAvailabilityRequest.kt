package net.kustax.opendelivery.data.request

import kotlinx.serialization.Serializable

@Serializable
data class SetProductAvailabilityRequest(
    val isAvailable: Boolean
)
