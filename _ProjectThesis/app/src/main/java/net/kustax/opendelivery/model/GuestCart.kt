package net.kustax.opendelivery.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.data.local.CartItemPref
import net.kustax.opendelivery.data.local.CartPreferences
import net.kustax.opendelivery.data.response.FoodMenuItemResponse
import net.kustax.opendelivery.data.response.FoodRestaurantResponse

data class FoodCartLine(
    val restaurant: FoodRestaurantResponse,
    val item: FoodMenuItemResponse,
    val quantity: Int
) {
    val key: String get() = dishItemKey(restaurant, item.id)
    val subtotalCents: Long get() = item.priceCents * quantity
}

// The single guest cart, holding items of one store at a time. In-room store items are persisted
// (CartPreferences); restaurant dishes are kept in memory.
class GuestCart(private val storePrefs: CartPreferences) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val storeLock = Mutex()
    private val foodLines = MutableStateFlow<List<FoodCartLine>>(emptyList())

    // null while the cart is empty
    val cart: Flow<Cart?> = combine(storePrefs.cartItems, foodLines) { stored, food ->
        val items = stored.mapNotNull { pref ->
            AppContainer.productRepository.getById(pref.productId)?.let { CartItem(it, pref.quantity) }
        }
        when {
            items.isNotEmpty() -> Cart.InRoom(items)
            food.isNotEmpty() -> Cart.Restaurant(food.first().restaurant, food)
            else -> null
        }
    }

    val itemCount: Flow<Int> = cart.map { it?.itemCount ?: 0 }

    // Quantity in the cart per BrowseItem.key
    val quantities: Flow<Map<String, Int>> = combine(storePrefs.cartItems, foodLines) { stored, food ->
        stored.associate { storeItemKey(it.productId) to it.quantity } + food.associate { it.key to it.quantity }
    }

    fun addStore(product: UiProduct) = editStore { items ->
        val idx = items.indexOfFirst { it.productId == product.id }
        if (idx < 0) {
            items.add(CartItemPref(product.id, product.name, 1, product.imageUrl))
        } else if (items[idx].quantity < MAX_QUANTITY) {
            items[idx] = items[idx].copy(quantity = items[idx].quantity + 1)
        }
    }

    // Removes the product when the quantity reaches zero
    fun decrementStore(productId: String) = editStore { items ->
        val idx = items.indexOfFirst { it.productId == productId }
        if (idx >= 0) {
            if (items[idx].quantity > 1) items[idx] = items[idx].copy(quantity = items[idx].quantity - 1)
            else items.removeAt(idx)
        }
    }

    fun addDish(restaurant: FoodRestaurantResponse, item: FoodMenuItemResponse) {
        foodLines.update { lines ->
            val key = dishItemKey(restaurant, item.id)
            val idx = lines.indexOfFirst { it.key == key }
            when {
                idx < 0 -> lines + FoodCartLine(restaurant, item, 1)
                lines[idx].quantity < MAX_QUANTITY ->
                    lines.toMutableList().also { it[idx] = it[idx].copy(quantity = it[idx].quantity + 1) }
                else -> lines
            }
        }
    }

    // Removes the dish when the quantity reaches zero
    fun decrementDish(key: String) {
        foodLines.update { lines ->
            lines.mapNotNull { line ->
                if (line.key == key) line.copy(quantity = line.quantity - 1).takeIf { it.quantity > 0 } else line
            }
        }
    }

    suspend fun clear() {
        foodLines.value = emptyList()
        storeLock.withLock { storePrefs.clearCart() }
    }

    private fun editStore(transform: (MutableList<CartItemPref>) -> Unit) {
        scope.launch {
            storeLock.withLock {
                val items = storePrefs.cartItems.first().toMutableList()
                transform(items)
                storePrefs.saveCart(items)
            }
        }
    }

    private companion object {
        const val MAX_QUANTITY = 99
    }
}
