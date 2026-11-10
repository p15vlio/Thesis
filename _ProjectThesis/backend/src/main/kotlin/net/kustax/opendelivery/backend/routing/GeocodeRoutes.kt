package net.kustax.opendelivery.backend.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kustax.opendelivery.backend.service.ForbiddenException
import net.kustax.opendelivery.backend.service.NotFoundException
import net.kustax.opendelivery.data.response.GeocodedAddressResponse
import net.kustax.opendelivery.domain.entity.platform.GeocodedAddress
import net.kustax.opendelivery.domain.enum.UserRole
import net.kustax.opendelivery.domain.port.Geocoder

fun Route.geocodeRoutes(geocoder: Geocoder) {
    route("/geocode") {
        get("/search") {
            call.requireOwnerOrAdmin()
            val query = call.request.queryParameters["q"]?.trim().orEmpty()
            require(query.length >= 3) { "Search text must be at least 3 characters" }
            call.respond(HttpStatusCode.OK, geocoder.search(query).map { it.toResponse() })
        }

        get("/reverse") {
            call.requireOwnerOrAdmin()
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            require(lat != null && lat in -90.0..90.0) { "lat must be a number between -90 and 90" }
            require(lon != null && lon in -180.0..180.0) { "lon must be a number between -180 and 180" }
            val address = geocoder.reverse(lat, lon) ?: throw NotFoundException("No address found for this location")
            call.respond(HttpStatusCode.OK, address.toResponse())
        }
    }
}

private fun ApplicationCall.requireOwnerOrAdmin() {
    if (userRole() == UserRole.DEVICE) throw ForbiddenException("Owner or platform admin access required")
}

private fun GeocodedAddress.toResponse() = GeocodedAddressResponse(
    displayName = displayName,
    streetName = streetName,
    streetNo = streetNo,
    postalCode = postalCode,
    area = area,
    city = city,
    country = country,
    latitude = latitude,
    longitude = longitude
)
