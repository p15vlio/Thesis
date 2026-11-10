package net.kustax.opendelivery.ui.screen.registration

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.data.local.DevicePreferences
import net.kustax.opendelivery.data.response.ErrorResponse
import net.kustax.opendelivery.data.remote.toUiProperty

// Mirrors the JSON built server-side in DeviceService.kt's qrContent strings.
@Serializable
private data class DeviceQrPayload(
    val deviceId: String,
    val activationCode: String,
    val schemaName: String,
    val endpoint: String = ""
)

class RegistrationViewModel(
    private val devicePreferences: DevicePreferences,
    private val context: Context
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
        object Success : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val serverUrl = MutableStateFlow("")
    val activationCode = MutableStateFlow("")

    init {
        viewModelScope.launch {
            AppContainer.pendingQrResult.collect { raw ->
                if (raw != null) {
                    AppContainer.pendingQrResult.value = null
                    onQrScanned(raw)
                }
            }
        }
    }

    fun onQrScanned(rawValue: String) {
        viewModelScope.launch {
            try {
                val payload = Json { ignoreUnknownKeys = true }.decodeFromString<DeviceQrPayload>(rawValue)
                val code = payload.activationCode
                val deviceId = payload.deviceId
                val schemaName = payload.schemaName

                if (code.isBlank() || deviceId.isBlank() || schemaName.isBlank()) {
                    _uiState.value = UiState.Error("Invalid QR code format")
                    return@launch
                }

                val resolvedUrl = payload.endpoint.ifBlank { serverUrl.value.trim() }
                if (resolvedUrl.isBlank()) {
                    _uiState.value = UiState.Error("Enter the server URL first, then scan the QR code")
                    return@launch
                }

                serverUrl.value = resolvedUrl
                activationCode.value = code
                activate(serverUrl = resolvedUrl, code = code, deviceId = deviceId, schemaName = schemaName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Could not parse QR code")
            }
        }
    }

    fun onManualSubmit() {
        val url = serverUrl.value.trim()
        val code = activationCode.value.trim()
        if (url.isBlank()) {
            _uiState.value = UiState.Error("Server URL is required")
            return
        }
        if (code.isBlank()) {
            _uiState.value = UiState.Error("Activation code is required")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val resolved = AppContainer.apiClient.resolveDevice(url, code)
                activate(serverUrl = url, code = code, deviceId = resolved.deviceId, schemaName = resolved.schemaName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Could not find device. Check the server URL and activation code.")
            }
        }
    }

    private suspend fun activate(serverUrl: String, code: String, deviceId: String, schemaName: String) {
        _uiState.value = UiState.Loading
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val response = AppContainer.apiClient.activateDevice(
                serverUrl = serverUrl,
                deviceId = deviceId,
                schemaName = schemaName,
                activationCode = code,
                androidId = androidId
            )
            devicePreferences.saveRegistration(
                serverUrl = serverUrl,
                deviceId = response.id,
                schemaName = schemaName,
                activationCode = code,
                propertyId = response.propertyId,
                propertyName = "Property ${response.propertyId}",
                deviceName = response.name,
                token = response.token ?: ""
            )
            devicePreferences.markRegistered()
            // Pre-load product catalog immediately so the home screen is ready without an app restart
            try {
                val products = AppContainer.realProductRepository.getAll(serverUrl, response.token ?: "")
                AppContainer.productRepository.replaceAll(products)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-fatal — catalog will load from the backend on the next app launch
            }
            // Pre-load the real property alongside the catalog — non-fatal if it fails, it is fetched again
            // on the next Home visit.
            try {
                val property = AppContainer.apiClient.getProperty(serverUrl, response.token ?: "", response.propertyId)
                AppContainer.propertyRepository.replace(property.toUiProperty())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-fatal — property will load from the backend on the next app launch
            }
            _uiState.value = UiState.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            _uiState.value = UiState.Error(activationErrorMessage(e))
        } catch (e: Exception) {
            _uiState.value = UiState.Error(ACTIVATION_FAILED)
        }
    }

    // The backend answers 404 for an unknown device, 403 for a wrong code and 409 with its reason in the body
    // (already activated, or the property or owner is deactivated)
    private suspend fun activationErrorMessage(e: ClientRequestException): String = when (e.response.status) {
        HttpStatusCode.NotFound -> "Activation code not found. Check the code and try again."
        HttpStatusCode.Forbidden -> "Wrong activation code. Check the code and try again."
        HttpStatusCode.Conflict -> serverMessage(e) ?: ACTIVATION_FAILED
        else -> ACTIVATION_FAILED
    }

    private suspend fun serverMessage(e: ClientRequestException): String? = try {
        e.response.body<ErrorResponse>().message.ifBlank { null }
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        null
    }
}

private const val ACTIVATION_FAILED = "Activation failed. Check URL and code."
