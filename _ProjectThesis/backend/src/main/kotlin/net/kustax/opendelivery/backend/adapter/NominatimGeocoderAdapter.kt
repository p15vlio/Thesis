package net.kustax.opendelivery.backend.adapter

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kustax.opendelivery.backend.service.ExternalServiceException
import net.kustax.opendelivery.domain.entity.platform.GeocodedAddress
import net.kustax.opendelivery.domain.port.Geocoder
import org.slf4j.LoggerFactory

// OpenStreetMap Nominatim (https://nominatim.org/release-docs/latest/api/Overview/). Usage policy
// (https://operations.osmfoundation.org/policies/nominatim/): identifying User-Agent, at most one request
// per second, no autocomplete. The rate limit is enforced here across the whole process.
class NominatimGeocoderAdapter : Geocoder {

    private val logger = LoggerFactory.getLogger(NominatimGeocoderAdapter::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
        }
    }
    private val rateLimitLock = Mutex()
    private var lastCallAt = 0L

    fun close() = client.close()

    override suspend fun search(query: String): List<GeocodedAddress> {
        val body = call("search") {
            parameter("limit", 5)
            parameter("countrycodes", "gr")
            parameter("q", query)
        }
        return decode<List<NominatimPlace>>(body).mapNotNull { it.toAddress() }
    }

    override suspend fun reverse(lat: Double, lon: Double): GeocodedAddress? {
        val body = call("reverse") {
            parameter("lat", lat)
            parameter("lon", lon)
        }
        val place = decode<NominatimPlace>(body)
        return if (place.error != null) null else place.toAddress()
    }

    private suspend fun call(endpoint: String, params: HttpRequestBuilder.() -> Unit): String {
        // Waiting for the 1-request-per-second slot is bounded: when many searches queue up at once, the ones
        // that would wait too long fail fast instead of piling up behind the lock.
        val acquired = withTimeoutOrNull(MAX_QUEUE_WAIT_MS) { rateLimitLock.lock() } != null
        if (!acquired) throw ExternalServiceException("Address search is busy. Try again in a moment.")
        val response = try {
            // Held for the whole request so calls are strictly serialized and spaced at least 1s apart.
            run {
                val wait = lastCallAt + MIN_INTERVAL_MS - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                try {
                    client.get("$BASE_URL/$endpoint") {
                        header("User-Agent", USER_AGENT)
                        header("Accept-Language", "el,en")
                        parameter("format", "jsonv2")
                        parameter("addressdetails", 1)
                        params()
                    }
                } finally {
                    lastCallAt = System.currentTimeMillis()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("[Nominatim] $endpoint request failed: ${e.message}", e)
            throw ExternalServiceException("Address service is unreachable or timed out. Try again later.")
        } finally {
            rateLimitLock.unlock()
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.bodyAsText()
            HttpStatusCode.TooManyRequests -> throw ExternalServiceException("Address service rate limit reached. Try again in a moment.")
            else -> {
                logger.error("[Nominatim] Unexpected status ${response.status} for $endpoint")
                throw ExternalServiceException("Address service returned an error (HTTP ${response.status.value})")
            }
        }
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (e: Exception) {
        logger.error("[Nominatim] Unreadable response: ${e.message}", e)
        throw ExternalServiceException("Address service returned an unreadable response")
    }

    private fun NominatimPlace.toAddress(): GeocodedAddress? {
        val latitude = lat?.toDoubleOrNull() ?: return null
        val longitude = lon?.toDoubleOrNull() ?: return null
        val a = address ?: NominatimAddress()
        return GeocodedAddress(
            displayName = displayName ?: return null,
            streetName = a.road,
            streetNo = a.houseNumber,
            postalCode = a.postcode,
            area = a.suburb ?: a.neighbourhood,
            city = a.city ?: a.town ?: a.village ?: a.municipality,
            country = a.country,
            latitude = latitude,
            longitude = longitude
        )
    }

    private companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org"
        const val USER_AGENT = "GuestDesk/1.0 (thesis project)"
        const val MIN_INTERVAL_MS = 1_000L
        const val MAX_QUEUE_WAIT_MS = 10_000L
    }
}

@Serializable
private data class NominatimPlace(
    val lat: String? = null,
    val lon: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val address: NominatimAddress? = null,
    val error: String? = null
)

@Serializable
private data class NominatimAddress(
    val road: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    val postcode: String? = null,
    val suburb: String? = null,
    val neighbourhood: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val country: String? = null
)
