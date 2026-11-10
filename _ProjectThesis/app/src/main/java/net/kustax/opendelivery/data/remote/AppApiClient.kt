package net.kustax.opendelivery.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.kustax.opendelivery.data.local.DevicePreferences
import net.kustax.opendelivery.data.request.ActivateDeviceRequest
import net.kustax.opendelivery.data.request.CreateOrderRequest
import net.kustax.opendelivery.data.request.InitiatePaymentRequest
import net.kustax.opendelivery.data.response.DeviceResolveResponse
import net.kustax.opendelivery.data.response.DeviceResponse
import net.kustax.opendelivery.data.response.FoodMenuItemResponse
import net.kustax.opendelivery.data.response.FoodRestaurantResponse
import net.kustax.opendelivery.data.response.OrderResponse
import net.kustax.opendelivery.data.response.PaymentResponse
import net.kustax.opendelivery.data.response.ProductResponse
import net.kustax.opendelivery.data.response.PropertyResponse
import net.kustax.opendelivery.model.UiProperty
import java.net.URLEncoder

// Path segments and query values are encoded: ids and codes come from the QR code or the guest
private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

class UnauthenticatedException(message: String) : RuntimeException(message)

class AppApiClient(private val devicePreferences: DevicePreferences) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                val clientException = exception as? ClientRequestException ?: return@handleResponseExceptionWithRequest
                if (clientException.response.status == HttpStatusCode.Unauthorized) {
                    devicePreferences.clearRegistration()
                    throw UnauthenticatedException("Token expired or invalid")
                }
            }
        }
    }

    suspend fun activateDevice(
        serverUrl: String,
        deviceId: String,
        schemaName: String,
        activationCode: String,
        androidId: String
    ): DeviceResponse {
        return client.post("$serverUrl/api/devices/${deviceId.urlEncoded()}/activate?schemaName=${schemaName.urlEncoded()}") {
            contentType(ContentType.Application.Json)
            setBody(ActivateDeviceRequest(activationCode = activationCode, androidDeviceId = androidId))
        }.body()
    }

    suspend fun resolveDevice(serverUrl: String, code: String): DeviceResolveResponse {
        return client.get("$serverUrl/api/devices/resolve?code=${code.urlEncoded()}").body()
    }

    suspend fun getProducts(serverUrl: String, token: String): List<ProductResponse> {
        return client.get("$serverUrl/api/products") {
            bearerAuth(token)
        }.body()
    }

    suspend fun getFoodRestaurants(serverUrl: String, token: String): List<FoodRestaurantResponse> {
        return client.get("$serverUrl/api/food/restaurants") {
            bearerAuth(token)
        }.body()
    }

    suspend fun getFoodMenu(
        serverUrl: String,
        token: String,
        platform: String,
        restaurantId: String
    ): List<FoodMenuItemResponse> {
        return client.get("$serverUrl/api/food/restaurants/${platform.urlEncoded()}/${restaurantId.urlEncoded()}/menu") {
            bearerAuth(token)
        }.body()
    }

    suspend fun createOrder(
        serverUrl: String,
        token: String,
        propertyId: String,
        request: CreateOrderRequest
    ): OrderResponse {
        return client.post("$serverUrl/api/orders") {
            parameter("propertyId", propertyId)
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun initiatePayment(
        serverUrl: String,
        token: String,
        request: InitiatePaymentRequest
    ): PaymentResponse {
        return client.post("$serverUrl/api/payments") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getOrder(serverUrl: String, token: String, orderId: String): OrderResponse {
        return client.get("$serverUrl/api/device/orders/${orderId.urlEncoded()}") {
            bearerAuth(token)
        }.body()
    }

    suspend fun getProperty(serverUrl: String, token: String, propertyId: String): PropertyResponse {
        return client.get("$serverUrl/api/properties/${propertyId.urlEncoded()}") {
            bearerAuth(token)
        }.body()
    }

    // Heartbeat: tells the backend this device is alive (shown to the owner as "last seen")
    suspend fun ping(serverUrl: String, token: String, deviceId: String) {
        client.patch("$serverUrl/api/devices/${deviceId.urlEncoded()}/ping") {
            bearerAuth(token)
        }
    }

    suspend fun cancelOrder(serverUrl: String, token: String, orderId: String): OrderResponse {
        return client.patch("$serverUrl/api/device/orders/${orderId.urlEncoded()}/cancel") {
            bearerAuth(token)
        }.body()
    }
}

fun PropertyResponse.toUiProperty(): UiProperty {
    val address = listOfNotNull(
        listOf(streetName, streetNo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
        area?.takeIf { it.isNotBlank() },
        postalCode.takeIf { it.isNotBlank() },
        city.takeIf { it.isNotBlank() }
    ).joinToString(", ")

    return UiProperty(
        id = id,
        name = name,
        address = address,
        contactPhone = contactPhone,
        wifiName = wifiName?.takeIf { it.isNotBlank() },
        wifiPassword = wifiPassword?.takeIf { it.isNotBlank() },
        checkOutTime = checkOutTime?.takeIf { it.isNotBlank() },
        houseRules = houseRules?.takeIf { it.isNotBlank() }
    )
}
