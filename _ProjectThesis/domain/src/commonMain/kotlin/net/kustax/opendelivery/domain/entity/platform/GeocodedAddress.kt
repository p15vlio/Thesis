package net.kustax.opendelivery.domain.entity.platform

data class GeocodedAddress(
    val displayName: String,
    val streetName: String?,
    val streetNo: String?,
    val postalCode: String?,
    val area: String?,
    val city: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
)
