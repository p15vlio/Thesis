package net.kustax.opendelivery.backend.adapter

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kustax.opendelivery.backend.service.ExternalServiceException
import net.kustax.opendelivery.domain.entity.platform.CompanyRegistryRecord
import net.kustax.opendelivery.domain.port.CompanyRegistry
import org.slf4j.LoggerFactory

// GEMH Open Data API (https://opendata-api.businessportal.gr). The API key is read from platform_settings
// on every lookup, so a key saved on the admin Settings page applies immediately without a restart.
class GemhCompanyRegistryAdapter(private val loadApiKey: suspend () -> String?) : CompanyRegistry {

    private val logger = LoggerFactory.getLogger(GemhCompanyRegistryAdapter::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
        }
    }

    fun close() = client.close()

    override suspend fun findByVatId(vatId: String): CompanyRegistryRecord? {
        val apiKey = loadApiKey() ?: throw ExternalServiceException("GEMH API key is not configured. Add it in Settings.")
        val response = try {
            client.get("$BASE_URL/companies") {
                header("api_key", apiKey)
                parameter("afm", vatId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("[GEMH] Lookup request failed: ${e.message}", e)
            throw ExternalServiceException("GEMH is unreachable or timed out. Try again later.")
        }
        return when (response.status) {
            HttpStatusCode.OK -> parse(response)
            HttpStatusCode.NotFound -> null
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw ExternalServiceException("GEMH rejected the API key")
            HttpStatusCode.TooManyRequests -> throw ExternalServiceException("GEMH rate limit reached. Try again in a moment.")
            else -> {
                logger.error("[GEMH] Unexpected status ${response.status} for lookup")
                throw ExternalServiceException("GEMH returned an error (HTTP ${response.status.value})")
            }
        }
    }

    private suspend fun parse(response: HttpResponse): CompanyRegistryRecord? {
        val results = try {
            json.decodeFromString<GemhSearchResponse>(response.bodyAsText()).searchResults
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("[GEMH] Unreadable response: ${e.message}", e)
            throw ExternalServiceException("GEMH returned an unreadable response")
        }
        // A VAT ID can match the head office and its branches; prefer the head office.
        val company = results.filter { it.coNameEl != null }.let { named -> named.firstOrNull { !it.isBranch } ?: named.firstOrNull() }
            ?: return null
        return CompanyRegistryRecord(
            vatId = company.afm ?: "",
            gemhNumber = company.arGemi?.toString(),
            companyName = company.coNameEl!!,
            distinctiveTitle = company.coTitlesEl.firstOrNull(),
            legalForm = company.legalType?.descr,
            companyActivity = mainActivity(company) ?: company.objective,
            streetName = company.street,
            streetNo = company.streetNumber,
            postalCode = company.zipCode,
            city = company.city ?: company.municipality?.descr,
            website = company.url,
            statusDescription = company.status?.descr,
            isActive = company.status?.isActive ?: true
        )
    }

    // The API does not enumerate the activity "type" values; the main activity is the one GEMH labels as
    // primary ("ΚΥΡΙΑ" / "MAIN" / "PRIMARY"). Falls back to the first listed activity.
    private fun mainActivity(company: GemhCompany): String? {
        val main = company.activities.firstOrNull { activity ->
            val type = activity.type?.uppercase().orEmpty()
            type.startsWith("ΚΥΡΙ") || type.startsWith("MAIN") || type.startsWith("PRIMARY")
        } ?: company.activities.firstOrNull()
        return main?.activity?.descr
    }

    private companion object {
        const val BASE_URL = "https://opendata-api.businessportal.gr/api/opendata/v1"
    }
}

@Serializable
private data class GemhSearchResponse(val searchResults: List<GemhCompany> = emptyList())

@Serializable
private data class GemhCompany(
    val arGemi: Long? = null,
    val afm: String? = null,
    val coNameEl: String? = null,
    val coTitlesEl: List<String> = emptyList(),
    val legalType: GemhDescription? = null,
    val status: GemhStatus? = null,
    val street: String? = null,
    val streetNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val municipality: GemhDescription? = null,
    val url: String? = null,
    val objective: String? = null,
    val isBranch: Boolean = false,
    val activities: List<GemhCompanyActivity> = emptyList()
)

@Serializable
private data class GemhDescription(val descr: String? = null)

@Serializable
private data class GemhStatus(val descr: String? = null, val isActive: Boolean? = null)

@Serializable
private data class GemhCompanyActivity(val activity: GemhDescription? = null, val type: String? = null)
