package net.kustax.opendelivery.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.model.UiProperty
import net.kustax.opendelivery.data.response.FoodRestaurantResponse
import net.kustax.opendelivery.model.Cart
import net.kustax.opendelivery.model.UiProduct
import net.kustax.opendelivery.model.restaurantKey
import net.kustax.opendelivery.ui.components.formatEuro
import net.kustax.opendelivery.ui.components.platformColor
import net.kustax.opendelivery.ui.components.platformLabel
import java.util.Calendar

// A Home card built from real catalog data; tapping it opens the store it came from
data class Highlight(
    val title: String,
    val subtitle: String,
    val color: Color,
    val storeKey: String
)

class HomeViewModel : ViewModel() {

    val property: StateFlow<UiProperty?> = AppContainer.propertyRepository.property

    val greeting: String = run {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val cartItemCount: StateFlow<Int> = AppContainer.guestCart.itemCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Where the cart icon leads: the store the cart's items come from (null while the cart is empty)
    val cartStoreKey: StateFlow<String?> = AppContainer.guestCart.cart
        .map { it?.storeKey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Track My Order is only offered once an order was placed during this visit
    val hasOrders: StateFlow<Boolean> = AppContainer.sessionOrders
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppContainer.sessionOrders.value.isNotEmpty())

    // Retried once per screen visit — covers a device that activated while the backend was
    // briefly unreachable, so the catalog can recover without an app restart.
    private val _isCatalogEmpty = MutableStateFlow(AppContainer.productRepository.getAll().isEmpty())
    val isCatalogEmpty: StateFlow<Boolean> = _isCatalogEmpty.asStateFlow()

    // Up to 2 restaurants + 2 in-room products (any free slots filled from the other kind), rotated daily
    val highlights: StateFlow<List<Highlight>> = combine(AppContainer.foodCatalog.restaurants, _isCatalogEmpty) { restaurants, _ ->
        buildHighlights(restaurants, AppContainer.productRepository.getAll().filter { it.isAvailable })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildHighlights(restaurants: List<FoodRestaurantResponse>, products: List<UiProduct>): List<Highlight> {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val pickedRestaurants = restaurants.sortedBy { it.restaurantKey }.rotatedBy(day)
        val pickedProducts = products.sortedBy { it.id }.rotatedBy(day)
        val restaurantCount = minOf(pickedRestaurants.size, (MAX_HIGHLIGHTS - minOf(pickedProducts.size, 2)).coerceAtLeast(2))
        val productCount = minOf(pickedProducts.size, MAX_HIGHLIGHTS - restaurantCount)
        val restaurantCards = pickedRestaurants.take(restaurantCount).map {
            Highlight(
                title = "Hungry? ${it.cuisine} from ${it.name}",
                subtitle = "Delivered in about ${it.etaMinutes} min via ${platformLabel(it.platform)}",
                color = platformColor(it.platform),
                storeKey = it.restaurantKey
            )
        }
        val productCards = pickedProducts.take(productCount).map {
            Highlight(
                title = it.name,
                subtitle = "${formatEuro(it.priceCents)} · brought to your room",
                color = Color(0xFFD97706),
                storeKey = Cart.IN_ROOM_KEY
            )
        }
        return restaurantCards.zipAlternating(productCards)
    }

    private fun <T> List<T>.rotatedBy(n: Int): List<T> =
        if (isEmpty()) this else (n % size).let { drop(it) + take(it) }

    private fun <T> List<T>.zipAlternating(other: List<T>): List<T> =
        (0 until maxOf(size, other.size)).flatMap { listOfNotNull(getOrNull(it), other.getOrNull(it)) }

    init {
        viewModelScope.launch {
            AppContainer.refreshCatalogAndProperty()
            _isCatalogEmpty.value = AppContainer.productRepository.getAll().isEmpty()
        }
    }

    private companion object {
        const val MAX_HIGHLIGHTS = 4
    }
}
