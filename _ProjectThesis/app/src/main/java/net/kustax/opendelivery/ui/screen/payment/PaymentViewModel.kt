package net.kustax.opendelivery.ui.screen.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.data.remote.toUiOrder
import net.kustax.opendelivery.data.request.CreateOrderRequest
import net.kustax.opendelivery.data.request.InitiatePaymentRequest
import net.kustax.opendelivery.data.request.OrderItemRequest
import net.kustax.opendelivery.data.response.OrderResponse
import net.kustax.opendelivery.model.Cart
import net.kustax.opendelivery.payment.CardEvent

// Pays the cart by card: an IN_ROOM_STORE order for the in-room store or a FOOD_DELIVERY order for a
// restaurant. There is no payment-method choice: whatever the guest does with the card (tap, insert,
// swipe) starts the payment.
class PaymentViewModel : ViewModel() {

    sealed class UiState {
        object Waiting : UiState()
        object PinEntry : UiState()
        object Processing : UiState()
        object Approved : UiState()
        // technical: the payment could not be attempted (network, server) rather than refused by the card scheme
        data class Declined(val reason: String, val technical: Boolean = false) : UiState()
        object ReaderUnavailable : UiState()
        // The card was charged but the restaurant platform rejected the order, so it was cancelled and refunded
        data class OrderCancelled(val message: String) : UiState()
    }

    private val _cart = MutableStateFlow<Cart?>(null)
    val cart: StateFlow<Cart?> = _cart.asStateFlow()

    // Backed by AppContainer so values persist across Store → Payment navigation
    val guestNotes = AppContainer.pendingNotes
    val guestEmail = AppContainer.pendingEmail

    private val _uiState = MutableStateFlow<UiState>(UiState.Waiting)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Set once createOrder succeeds for the current payment, so a retry after a decline re-uses the
    // same PENDING order instead of creating a duplicate one.
    private var createdOrder: OrderResponse? = null

    // Waiting for a card, or a decline message on screen before returning to waiting
    private var job: Job? = null

    init {
        viewModelScope.launch {
            _cart.value = AppContainer.guestCart.cart.first()
        }
    }

    // The screen is visible: listen for a card (also used by the "Try again" button)
    fun resumeWaiting() {
        if (job?.isActive == true) return
        when (_uiState.value) {
            UiState.PinEntry, UiState.Processing, UiState.Approved, is UiState.OrderCancelled -> return
            else -> Unit
        }
        job = viewModelScope.launch { listen() }
    }

    // The screen is hidden: the reader must not keep waiting in the background
    fun stopWaiting() {
        when (_uiState.value) {
            UiState.Waiting, is UiState.Declined, UiState.ReaderUnavailable -> job?.cancel()
            else -> Unit
        }
    }

    // Waits for cards until one ends the wait: a chip (PIN entry), an approval or an unavailable reader.
    // A decline shows its reason and then keeps listening so the guest can retry.
    private suspend fun listen() {
        do {
            _uiState.value = UiState.Waiting
            val keepListening = handle(AppContainer.cardTerminal.awaitCard())
        } while (keepListening)
    }

    // Returns true when the guest should be given another go
    private suspend fun handle(event: CardEvent): Boolean = when (event) {
        CardEvent.Tapped -> pay(ENTRY_CONTACTLESS, pinVerified = null, cardLast4 = null)
        CardEvent.Inserted -> {
            _uiState.value = UiState.PinEntry
            false
        }
        is CardEvent.Swiped -> pay(ENTRY_MAGSTRIPE, pinVerified = null, cardLast4 = event.last4)
        CardEvent.ReadFailed -> decline("Card could not be read — please try again", technical = true)
        CardEvent.Unavailable -> {
            _uiState.value = UiState.ReaderUnavailable
            false
        }
    }

    // PIN verification is SIMULATED for the demo: the secure PIN pad of the reader needs a PIN key loaded by an
    // acquirer, so the app draws its own keypad and treats 0000 as the wrong PIN and any other 4 digits
    // as correct. The PIN stays in this call: it is never stored, logged or sent, only the boolean outcome.
    fun submitPin(pin: String) {
        val pinVerified = pin != WRONG_DEMO_PIN
        job = viewModelScope.launch {
            if (pay(ENTRY_CHIP, pinVerified = pinVerified, cardLast4 = null)) listen()
        }
    }

    fun cancelPin() {
        _uiState.value = UiState.Waiting
        resumeWaiting()
    }

    private suspend fun pay(entryMode: String, pinVerified: Boolean?, cardLast4: String?): Boolean {
        // A card can be presented before the cart has finished loading
        val cart = _cart.value ?: AppContainer.guestCart.cart.first()
        if (cart == null || !cart.isPayable) {
            return decline("Your cart is empty")
        }
        _uiState.value = UiState.Processing
        try {
            val prefs = AppContainer.devicePreferences.registrationState.first()

            val order = createdOrder ?: AppContainer.apiClient.createOrder(
                serverUrl = prefs.serverUrl,
                token = prefs.token,
                propertyId = prefs.propertyId,
                request = orderRequest(cart)
            ).also { createdOrder = it }

            val payment = AppContainer.apiClient.initiatePayment(
                serverUrl = prefs.serverUrl,
                token = prefs.token,
                request = InitiatePaymentRequest(
                    orderId = order.id,
                    entryMode = entryMode,
                    pinVerified = pinVerified,
                    cardLast4 = cardLast4
                )
            )
            if (payment.orderStatus == ORDER_CANCELLED) {
                resetCheckout()
                _uiState.value = UiState.OrderCancelled(payment.message ?: DEFAULT_CANCELLED_MESSAGE)
                return false
            }
            if (payment.status != STATUS_COMPLETED) {
                return decline(declineMessage(entryMode, payment.declineReason))
            }

            approve(order.copy(status = ORDER_CONFIRMED))
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The request may have failed after the server already charged the card (a lost response, or a
            // retry answered "already paid"): ask the server what happened to the order before declining
            if (recoverSettledOrder()) return false
            return decline("Could not complete the payment — please try again", technical = true)
        }
    }

    private suspend fun approve(order: OrderResponse) {
        resetCheckout()
        AppContainer.sessionOrders.update { it + order.toUiOrder() }
        _uiState.value = UiState.Approved
    }

    // Returns true when the current order turns out to be settled on the server: paid (shown as approved) or
    // cancelled after payment (shown with its refund message). A still-PENDING order was not paid.
    private suspend fun recoverSettledOrder(): Boolean {
        val pending = createdOrder ?: return false
        val order = try {
            val prefs = AppContainer.devicePreferences.registrationState.first()
            AppContainer.apiClient.getOrder(prefs.serverUrl, prefs.token, pending.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        return when (order.status) {
            ORDER_PENDING -> false
            ORDER_CANCELLED -> {
                resetCheckout()
                _uiState.value = UiState.OrderCancelled(RECOVERED_CANCELLED_MESSAGE)
                true
            }
            else -> {
                approve(order)
                true
            }
        }
    }

    // The order is settled (placed or cancelled): the next checkout starts from an empty cart
    private suspend fun resetCheckout() {
        AppContainer.guestCart.clear()
        AppContainer.pendingNotes.value = ""
        AppContainer.pendingEmail.value = ""
        createdOrder = null
    }

    // Shows why the payment was declined for a moment; the caller then listens for the next card
    private suspend fun decline(reason: String, technical: Boolean = false): Boolean {
        _uiState.value = UiState.Declined(reason, technical)
        delay(DECLINE_SHOWN_MS)
        return true
    }

    private fun declineMessage(entryMode: String, serverReason: String?): String = when (entryMode) {
        ENTRY_CHIP -> "Incorrect PIN — payment declined"
        ENTRY_MAGSTRIPE -> "Swipe not accepted — please tap or insert your card"
        else -> serverReason ?: "Payment declined"
    }

    override fun onCleared() {
        AppContainer.cardTerminal.release()
    }

    private fun orderRequest(cart: Cart): CreateOrderRequest {
        val email = AppContainer.pendingEmail.value.ifBlank { null }
        val notes = AppContainer.pendingNotes.value.ifBlank { null }
        return when (cart) {
            is Cart.InRoom -> CreateOrderRequest(
                guestEmail = email,
                type = "IN_ROOM_STORE",
                items = cart.items.map { OrderItemRequest(productId = it.product.id, quantity = it.quantity) },
                notes = notes
            )

            is Cart.Restaurant -> CreateOrderRequest(
                guestEmail = email,
                type = "FOOD_DELIVERY",
                items = cart.lines.map { OrderItemRequest(productId = it.item.id, quantity = it.quantity) },
                notes = notes,
                platform = cart.restaurant.platform,
                restaurantId = cart.restaurant.id
            )
        }
    }

    private companion object {
        const val ENTRY_CONTACTLESS = "CARD_CONTACTLESS"
        const val ENTRY_CHIP = "CARD_CHIP"
        const val ENTRY_MAGSTRIPE = "CARD_MAGSTRIPE"
        const val STATUS_COMPLETED = "COMPLETED"
        const val ORDER_PENDING = "PENDING"
        const val ORDER_CONFIRMED = "CONFIRMED"
        const val ORDER_CANCELLED = "CANCELLED"
        const val DEFAULT_CANCELLED_MESSAGE =
            "The restaurant couldn't accept your order. Your payment has been refunded."
        const val RECOVERED_CANCELLED_MESSAGE =
            "This order was cancelled. Any payment made for it is refunded to your card."
        const val WRONG_DEMO_PIN = "0000"
        const val DECLINE_SHOWN_MS = 3_500L
    }
}
