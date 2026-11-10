package net.kustax.opendelivery.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.cartDataStore: DataStore<Preferences> by preferencesDataStore(name = "cart_prefs")

// Carts saved by older versions may hold fields that no longer exist
private val cartJson = Json { ignoreUnknownKeys = true }

@Serializable
data class CartItemPref(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val imageUrl: String? = null
)

class CartPreferences(private val context: Context) {

    private val CART_ITEMS_KEY = stringPreferencesKey("cart_items")

    val cartItems: Flow<List<CartItemPref>> = context.cartDataStore.data.map { prefs ->
        val raw = prefs[CART_ITEMS_KEY] ?: return@map emptyList()
        try {
            cartJson.decodeFromString<List<CartItemPref>>(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCart(items: List<CartItemPref>) {
        context.cartDataStore.edit { prefs ->
            prefs[CART_ITEMS_KEY] = cartJson.encodeToString(items)
        }
    }

    suspend fun clearCart() {
        context.cartDataStore.edit { it.remove(CART_ITEMS_KEY) }
    }
}
