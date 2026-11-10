package net.kustax.opendelivery.web.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kustax.opendelivery.data.request.*
import net.kustax.opendelivery.data.response.*
import net.kustax.opendelivery.web.auth.AuthSession
import net.kustax.opendelivery.web.auth.SessionController

class ApiClient(private val baseUrl: String, token: String) {

    companion object {
        const val BASE_URL = ""

        fun unauthenticated(): ApiClient = ApiClient(BASE_URL, "")
    }

    // Mutable so the token refresh logic can update it without rebuilding the client.
    private var currentToken: String = token
    private val refreshMutex = Mutex()

    private val client = HttpClient(Js) {
        // Without this, non-2xx responses are deserialized as the expected type instead of reaching the
        // validator below — error bodies surfaced as JSON parse errors and the 401 refresh never ran.
        expectSuccess = true
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                val responseException = exception as? ResponseException ?: return@handleResponseExceptionWithRequest
                // Let 401 propagate as ResponseException so withTokenRefresh can detect it.
                if (responseException.response.status == HttpStatusCode.Unauthorized) throw responseException
                val message = try {
                    responseException.response.body<ErrorResponse>().message
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    "Request failed (${responseException.response.status.value})"
                }
                throw Exception(message)
            }
        }
    }

    private suspend fun <T> withTokenRefresh(block: suspend () -> T): T {
        val tokenUsed = currentToken
        return try {
            block()
        } catch (e: ResponseException) {
            if (e.response.status != HttpStatusCode.Unauthorized) throw e
            refreshSession(tokenUsed, e)
            try {
                block()
            } catch (retryError: ResponseException) {
                // Still unauthorized with a freshly refreshed token: the session is unusable.
                if (retryError.response.status != HttpStatusCode.Unauthorized) throw retryError
                SessionController.markExpired()
                throw Exception("Your session has expired. Please sign in again.")
            }
        }
    }

    // Requests that fail with 401 at the same time share one refresh: refresh tokens rotate, so a second
    // refresh with the already used token would fail and wrongly end the session.
    private suspend fun refreshSession(staleToken: String, originalException: ResponseException) {
        refreshMutex.withLock {
            if (currentToken != staleToken) return // another request already refreshed
            val session = AuthSession.load() ?: run {
                SessionController.markExpired()
                throw originalException
            }
            try {
                val newTokens = client.post("$baseUrl/api/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(session.refreshToken))
                }.body<TokenResponse>()
                session.copy(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken
                ).save()
                currentToken = newTokens.accessToken
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SessionController.markExpired()
                throw originalException
            }
        }
    }

    private suspend inline fun <reified T> get(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): T =
        withTokenRefresh {
            client.get("$baseUrl$path") {
                bearerAuth(currentToken)
                block()
            }.body()
        }

    private suspend inline fun <reified T> post(path: String, body: Any): T =
        withTokenRefresh {
            client.post("$baseUrl$path") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
        }

    private suspend inline fun <reified T> put(path: String, body: Any): T =
        withTokenRefresh {
            client.put("$baseUrl$path") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
        }

    private suspend inline fun <reified T> patch(path: String, body: Any): T =
        withTokenRefresh {
            client.patch("$baseUrl$path") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
        }

    private suspend fun delete(path: String) {
        withTokenRefresh {
            client.delete("$baseUrl$path") { bearerAuth(currentToken) }
        }
    }

    private suspend fun patchNoBody(path: String) {
        withTokenRefresh {
            client.patch("$baseUrl$path") { bearerAuth(currentToken) }
        }
    }

    // Unauthenticated — bypasses withTokenRefresh entirely.
    suspend fun login(request: LoginRequest): TokenResponse {
        val response = client.post("$baseUrl/api/auth/login") {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            val message = try {
                response.body<ErrorResponse>().message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "Login failed (${response.status.value})"
            }
            throw Exception(message)
        }
        return response.body()
    }

    // Own password — PLATFORM_ADMIN and OWNER. The server signs out every other session of the user and returns
    // a fresh token pair, which replaces the stored session so this browser stays signed in.
    suspend fun changePassword(request: ChangePasswordRequest) {
        val tokens: TokenResponse = post("/api/auth/change-password", request)
        AuthSession.load()?.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken)?.save()
        currentToken = tokens.accessToken
    }

    // Platform settings — PLATFORM_ADMIN
    suspend fun getSmtpSettings(): SmtpSettingsResponse = get("/api/settings/smtp")
    suspend fun updateSmtpSettings(request: UpdateSmtpSettingsRequest): SmtpSettingsResponse = put("/api/settings/smtp", request)
    suspend fun getGemhSettings(): GemhSettingsResponse = get("/api/settings/gemh")
    suspend fun updateGemhSettings(request: UpdateGemhSettingsRequest): GemhSettingsResponse = put("/api/settings/gemh", request)
    suspend fun sendTestEmail(toEmail: String) {
        withTokenRefresh {
            client.post("$baseUrl/api/settings/smtp/test") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                setBody(SendTestEmailRequest(toEmail))
            }
        }
    }

    // Address search — OWNER and PLATFORM_ADMIN
    suspend fun searchAddress(query: String): List<GeocodedAddressResponse> = get("/api/geocode/search") { parameter("q", query) }
    suspend fun reverseGeocode(lat: Double, lon: Double): GeocodedAddressResponse =
        get("/api/geocode/reverse") { parameter("lat", lat); parameter("lon", lon) }

    // Owners — PLATFORM_ADMIN
    suspend fun lookupCompany(vatId: String): CompanyLookupResponse = get("/api/company-lookup") { parameter("vatId", vatId) }
    suspend fun createOwner(request: CreateOwnerRequest): OwnerResponse = post("/api/owners", request)
    suspend fun updateOwner(id: String, request: UpdateOwnerRequest): OwnerResponse = put("/api/owners/$id", request)
    suspend fun deactivateOwner(id: String) = patchNoBody("/api/owners/$id/deactivate")
    suspend fun activateOwner(id: String) = patchNoBody("/api/owners/$id/activate")
    suspend fun resetOwnerPassword(id: String): OwnerResponse =
        withTokenRefresh { client.patch("$baseUrl/api/owners/$id/reset-password") { bearerAuth(currentToken) }.body() }

    // Properties — OWNER
    suspend fun listProperties(): List<PropertyResponse> = get("/api/properties")
    suspend fun createProperty(request: CreatePropertyRequest): PropertyResponse = post("/api/properties", request)
    suspend fun updateProperty(id: String, request: UpdatePropertyRequest): PropertyResponse = put("/api/properties/$id", request)
    suspend fun deactivateProperty(id: String) = patchNoBody("/api/properties/$id/deactivate")
    suspend fun activateProperty(id: String) = patchNoBody("/api/properties/$id/activate")

    // Devices — OWNER
    suspend fun listDevices(propertyId: String): List<DeviceResponse> = get("/api/properties/$propertyId/devices")
    suspend fun createDevice(propertyId: String, request: CreateDeviceRequest): DeviceResponse = post("/api/properties/$propertyId/devices", request)
    suspend fun deactivateDevice(id: String) = patchNoBody("/api/devices/$id/deactivate")
    suspend fun reactivateDevice(id: String) = patchNoBody("/api/devices/$id/reactivate")
    suspend fun unregisterDevice(id: String): DeviceResponse =
        withTokenRefresh {
            client.post("$baseUrl/api/devices/$id/unregister") { bearerAuth(currentToken) }.body()
        }
    suspend fun getDeviceOrders(deviceId: String): List<OrderResponse> = get("/api/devices/$deviceId/orders")

    // Products — OWNER
    suspend fun listProducts(): List<ProductResponse> = get("/api/products")
    suspend fun createProduct(request: CreateProductRequest): ProductResponse = post("/api/products", request)
    suspend fun updateProduct(id: String, request: UpdateProductRequest): ProductResponse = put("/api/products/$id", request)

    // Dedicated patch for availability — must supply all required UpdateProductRequest fields
    suspend fun toggleAvailability(product: ProductResponse, isAvailable: Boolean): ProductResponse =
        patch(
            "/api/products/${product.id}/availability",
            UpdateProductRequest(
                name = product.name,
                description = product.description,
                basePrice = product.basePrice,
                imageUrl = product.imageUrl,
                isAvailable = isAvailable
            )
        )

    // Per-property availability override
    suspend fun setProductPropertyAvailability(
        productId: String,
        propertyId: String,
        isAvailable: Boolean,
        schemaName: String? = null
    ): ProductResponse =
        withTokenRefresh {
            client.patch("$baseUrl/api/products/$productId/properties/$propertyId/availability") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                schemaName?.let { parameter("schemaName", it) }
                setBody(SetProductAvailabilityRequest(isAvailable))
            }.body()
        }

    // Food delivery platforms — OWNER; PLATFORM_ADMIN reads and sets property availability via schemaName
    suspend fun listFoodPlatforms(schemaName: String? = null): List<FoodPlatformResponse> =
        get("/api/food-platforms") { schemaName?.let { parameter("schemaName", it) } }

    suspend fun connectFoodPlatform(platform: String, apiKey: String): FoodPlatformResponse =
        put("/api/food-platforms/$platform", ConnectFoodPlatformRequest(apiKey))

    suspend fun disconnectFoodPlatform(platform: String) = delete("/api/food-platforms/$platform")

    suspend fun getPropertyFoodPlatforms(propertyId: String, schemaName: String? = null): PropertyFoodPlatformsResponse =
        get("/api/properties/$propertyId/food-platforms") { schemaName?.let { parameter("schemaName", it) } }

    suspend fun setPropertyFoodPlatforms(propertyId: String, platforms: List<String>, schemaName: String? = null): PropertyFoodPlatformsResponse =
        withTokenRefresh {
            client.put("$baseUrl/api/properties/$propertyId/food-platforms") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                schemaName?.let { parameter("schemaName", it) }
                setBody(SetPropertyFoodPlatformsRequest(platforms))
            }.body()
        }

    suspend fun listFoodPlatformCatalog(platform: String, schemaName: String? = null): List<CatalogProductResponse> =
        get("/api/food-platforms/$platform/catalog") { schemaName?.let { parameter("schemaName", it) } }

    suspend fun importProducts(request: ImportProductsRequest, schemaName: String? = null): ImportProductsResponse =
        withTokenRefresh {
            client.post("$baseUrl/api/products/import") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                schemaName?.let { parameter("schemaName", it) }
                setBody(request)
            }.body()
        }

    // Dashboard — OWNER
    suspend fun getDashboardSummary(): DashboardSummaryResponse = get("/api/dashboard/summary")

    // Orders — OWNER; propertyId is always required by the backend
    suspend fun listOrders(propertyId: String, status: String? = null): List<OrderResponse> =
        get("/api/orders") {
            parameter("propertyId", propertyId)
            status?.let { parameter("status", it) }
        }


    suspend fun updateOrderStatus(id: String, status: String): OrderResponse =
        patch("/api/orders/$id/status", UpdateOrderStatusRequest(status))

    // Admin — PLATFORM_ADMIN
    suspend fun getOwnerStats(): List<OwnerWithStatsResponse> = get("/api/admin/owners/stats")
    suspend fun getAdminOrderSummary(): AdminOrderSummaryResponse = get("/api/admin/orders/summary")
    suspend fun listAuditLogs(limit: Int, offset: Long): List<AuditLogResponse> =
        get("/api/audit-logs") {
            parameter("limit", limit)
            parameter("offset", offset)
        }

    // Admin cross-tenant reads — schemaName passed as query param
    suspend fun listPropertiesAdmin(schemaName: String): List<PropertyResponse> =
        get("/api/properties") { parameter("schemaName", schemaName) }

    suspend fun listDevicesAdmin(propertyId: String, schemaName: String): List<DeviceResponse> =
        get("/api/properties/$propertyId/devices") { parameter("schemaName", schemaName) }

    suspend fun listProductsAdmin(schemaName: String): List<ProductResponse> =
        get("/api/products") { parameter("schemaName", schemaName) }

    suspend fun listOrdersAdmin(propertyId: String, schemaName: String, status: String? = null): List<OrderResponse> =
        get("/api/orders") {
            parameter("propertyId", propertyId)
            parameter("schemaName", schemaName)
            status?.let { parameter("status", it) }
        }

    // Admin cross-tenant mutations — schemaName passed as query param
    suspend fun createPropertyAdmin(schemaName: String, request: CreatePropertyRequest): PropertyResponse =
        withTokenRefresh {
            client.post("$baseUrl/api/properties?schemaName=$schemaName") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    suspend fun createProductAdmin(schemaName: String, request: CreateProductRequest): ProductResponse =
        withTokenRefresh {
            client.post("$baseUrl/api/products") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                parameter("schemaName", schemaName)
                setBody(request)
            }.body()
        }

    suspend fun createDeviceAdmin(propertyId: String, schemaName: String, request: CreateDeviceRequest): DeviceResponse =
        withTokenRefresh {
            client.post("$baseUrl/api/properties/$propertyId/devices") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                parameter("schemaName", schemaName)
                setBody(request)
            }.body()
        }

    suspend fun updatePropertyAdmin(id: String, schemaName: String, request: UpdatePropertyRequest): PropertyResponse =
        withTokenRefresh {
            client.put("$baseUrl/api/properties/$id?schemaName=$schemaName") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    suspend fun deactivatePropertyAdmin(id: String, schemaName: String) =
        withTokenRefresh {
            client.patch("$baseUrl/api/properties/$id/deactivate?schemaName=$schemaName") { bearerAuth(currentToken) }
        }

    suspend fun activatePropertyAdmin(id: String, schemaName: String) =
        withTokenRefresh {
            client.patch("$baseUrl/api/properties/$id/activate?schemaName=$schemaName") { bearerAuth(currentToken) }
        }

    suspend fun deactivateDeviceAdmin(id: String, schemaName: String) =
        withTokenRefresh {
            client.patch("$baseUrl/api/devices/$id/deactivate?schemaName=$schemaName") { bearerAuth(currentToken) }
        }

    suspend fun reactivateDeviceAdmin(id: String, schemaName: String) =
        withTokenRefresh {
            client.patch("$baseUrl/api/devices/$id/reactivate?schemaName=$schemaName") { bearerAuth(currentToken) }
        }

    suspend fun unregisterDeviceAdmin(id: String, schemaName: String): DeviceResponse =
        withTokenRefresh {
            client.post("$baseUrl/api/devices/$id/unregister?schemaName=$schemaName") { bearerAuth(currentToken) }.body()
        }

    suspend fun getDeviceOrdersAdmin(deviceId: String, schemaName: String): List<OrderResponse> =
        get("/api/devices/$deviceId/orders") { parameter("schemaName", schemaName) }

    suspend fun toggleAvailabilityAdmin(schemaName: String, product: ProductResponse, isAvailable: Boolean): ProductResponse =
        withTokenRefresh {
            client.patch("$baseUrl/api/products/${product.id}/availability?schemaName=$schemaName") {
                bearerAuth(currentToken)
                contentType(ContentType.Application.Json)
                setBody(
                    UpdateProductRequest(
                        name = product.name,
                        description = product.description,
                        basePrice = product.basePrice,
                        imageUrl = product.imageUrl,
                        isAvailable = isAvailable
                    )
                )
            }.body()
        }

    // Delete operations
    suspend fun deleteProperty(id: String) = delete("/api/properties/$id")
    suspend fun deleteDevice(id: String) = delete("/api/devices/$id")
    suspend fun deleteProduct(id: String) = delete("/api/products/$id")

    suspend fun deleteProductAdmin(id: String, schemaName: String) {
        withTokenRefresh {
            client.delete("$baseUrl/api/products/$id") {
                bearerAuth(currentToken)
                parameter("schemaName", schemaName)
            }.let { if (!it.status.isSuccess()) throw Exception("Failed to delete product") }
        }
    }

    suspend fun getPayments(orderId: String): List<PaymentResponse> {
        return withTokenRefresh {
            client.get("$baseUrl/api/orders/$orderId/payments") {
                bearerAuth(currentToken)
            }.body()
        }
    }

    // Health check — unauthenticated, must NOT go through withTokenRefresh.
    suspend fun checkHealth(): Boolean = try {
        client.get("$baseUrl/api/health") { expectSuccess = false }.status.value == 200
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
