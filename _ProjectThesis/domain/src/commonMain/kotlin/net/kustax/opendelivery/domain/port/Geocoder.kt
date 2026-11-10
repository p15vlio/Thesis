package net.kustax.opendelivery.domain.port

import net.kustax.opendelivery.domain.entity.platform.GeocodedAddress

// Address search and reverse geocoding (OpenStreetMap Nominatim)
interface Geocoder {
    suspend fun search(query: String): List<GeocodedAddress>
    suspend fun reverse(lat: Double, lon: Double): GeocodedAddress?
}
