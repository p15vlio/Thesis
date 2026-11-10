package net.kustax.opendelivery.ui.screen.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.data.remote.toUiOrder
import net.kustax.opendelivery.model.UiOrder

// Tracks every order placed during the current visit (store and food orders together)
class OrderTrackingViewModel : ViewModel() {

    val orders: StateFlow<List<UiOrder>> = AppContainer.sessionOrders

    // One-off message for the guest, e.g. a failed cancellation; cleared once shown
    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private var serverUrl = ""
    private var token = ""

    init {
        viewModelScope.launch {
            val prefs = AppContainer.devicePreferences.registrationState.first()
            serverUrl = prefs.serverUrl
            token = prefs.token
            poll()
        }
    }

    // Food orders advance every ~45 s on the delivery platform, so they are refreshed every tick
    // (5 s) while store orders only on every third tick (15 s). Stops once all orders are final.
    private suspend fun poll() {
        var tick = 0
        while (true) {
            orders.value
                .filter { !it.isFinal && (it.isFoodDelivery || tick % STORE_POLL_EVERY_TICKS == 0) }
                .forEach { refresh(it.id) }
            if (orders.value.all { it.isFinal }) break
            delay(POLL_INTERVAL_MS)
            tick++
        }
    }

    private suspend fun refresh(orderId: String) {
        try {
            replace(AppContainer.apiClient.getOrder(serverUrl, token, orderId).toUiOrder())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The next poll tries again
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            try {
                replace(AppContainer.apiClient.cancelOrder(serverUrl, token, orderId).toUiOrder())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _feedback.value = "Could not cancel the order. Please try again or ask the front desk."
            }
        }
    }

    private fun replace(updated: UiOrder) {
        AppContainer.sessionOrders.update { list -> list.map { if (it.id == updated.id) updated else it } }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val STORE_POLL_EVERY_TICKS = 3
    }
}
