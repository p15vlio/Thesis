package net.kustax.opendelivery

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.local.CartPreferences
import net.kustax.opendelivery.data.local.DevicePreferences
import net.kustax.opendelivery.data.remote.AppApiClient
import net.kustax.opendelivery.data.remote.FoodCatalog
import net.kustax.opendelivery.data.remote.MutableProductRepository
import net.kustax.opendelivery.data.remote.MutablePropertyRepository
import net.kustax.opendelivery.data.remote.RealProductRepository
import net.kustax.opendelivery.data.remote.toUiProperty
import net.kustax.opendelivery.model.GuestCart
import net.kustax.opendelivery.model.UiOrder
import net.kustax.opendelivery.payment.CardTerminal
import net.kustax.opendelivery.payment.DeviceCardTerminal

object AppContainer {
    lateinit var devicePreferences: DevicePreferences
    lateinit var cartPreferences: CartPreferences

    lateinit var apiClient: AppApiClient
    lateinit var foodCatalog: FoodCatalog
    lateinit var guestCart: GuestCart
    lateinit var realProductRepository: RealProductRepository
    lateinit var cardTerminal: CardTerminal

    // Mutable product store — filled from the backend after activation.
    val productRepository: MutableProductRepository = MutableProductRepository()

    val propertyRepository: MutablePropertyRepository = MutablePropertyRepository()

    // Used to route ZXing QR scan results from MainActivity.onActivityResult → RegistrationViewModel
    val pendingQrResult: MutableStateFlow<String?> = MutableStateFlow(null)

    // Persists guest notes and email across Cart → Payment navigation
    val pendingNotes: MutableStateFlow<String> = MutableStateFlow("")
    val pendingEmail: MutableStateFlow<String> = MutableStateFlow("")

    // Orders placed during the current guest visit (cleared when the kiosk returns to idle); the
    // tracking and confirmation screens show these together.
    val sessionOrders: MutableStateFlow<List<UiOrder>> = MutableStateFlow(emptyList())

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        devicePreferences = DevicePreferences(context)
        cartPreferences = CartPreferences(context)
        apiClient = AppApiClient(devicePreferences)
        foodCatalog = FoodCatalog(apiClient, devicePreferences)
        guestCart = GuestCart(cartPreferences)
        realProductRepository = RealProductRepository(apiClient)
        cardTerminal = DeviceCardTerminal(context)

        // Load real products from the backend once the registered device state is available
        appScope.launch { refreshCatalogAndProperty() }
        appScope.launch { sendHeartbeats() }
    }

    // Reports to the backend once a minute that this device is alive, for as long as the app runs. A 401
    // (device deactivated or unregistered) clears the registration in AppApiClient, which sends the kiosk
    // back to the registration screen even while nobody is using it. Network failures are simply retried.
    private suspend fun sendHeartbeats() {
        while (true) {
            val state = devicePreferences.registrationState.first()
            if (state.isRegistered && state.serverUrl.isNotBlank() && state.deviceId.isNotBlank()) {
                try {
                    apiClient.ping(state.serverUrl, state.token, state.deviceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Offline or server unreachable: try again at the next beat
                }
            }
            delay(HEARTBEAT_INTERVAL_MS)
        }
    }

    private const val HEARTBEAT_INTERVAL_MS = 60_000L

    // Ends a guest visit: nothing of the previous guest (cart, email, notes, orders) may reach the next one
    suspend fun endGuestSession() {
        guestCart.clear()
        pendingEmail.value = ""
        pendingNotes.value = ""
        sessionOrders.value = emptyList()
    }

    // Fetches the product catalog when it is empty and refreshes the property, so guest information the owner
    // edited shows up without an app restart. Called once at init and on every Home visit — which also lets a
    // device that activated while the backend was briefly unreachable recover. Failures are non-fatal: the
    // catalog stays empty and the previously known property stays in place until the next attempt.
    suspend fun refreshCatalogAndProperty() {
        val state = devicePreferences.registrationState.first()
        if (!(state.isRegistered && state.serverUrl.isNotBlank() && state.schemaName.isNotBlank())) return
        if (productRepository.getAll().isEmpty()) {
            try {
                val products = realProductRepository.getAll(state.serverUrl, state.token)
                productRepository.replaceAll(products)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Products remain empty until the next retry
            }
        }
        try {
            propertyRepository.replace(apiClient.getProperty(state.serverUrl, state.token, state.propertyId).toUiProperty())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keeps the previously known property
        }
    }
}
