package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class GeocodedAddressResponse(
    val displayName: String,
    val streetName: String? = null,
    val streetNo: String? = null,
    val postalCode: String? = null,
    val area: String? = null,
    val city: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double
)
