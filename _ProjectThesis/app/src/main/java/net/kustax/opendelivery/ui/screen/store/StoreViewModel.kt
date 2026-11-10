package net.kustax.opendelivery.ui.screen.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.data.response.FoodRestaurantResponse
import net.kustax.opendelivery.model.BrowseItem
import net.kustax.opendelivery.model.Cart
import net.kustax.opendelivery.model.FoodCartLine
import net.kustax.opendelivery.model.UiProduct
import net.kustax.opendelivery.model.restaurantKey

// Browses one store: the in-room store (storeKey == Cart.IN_ROOM_KEY) or a single restaurant
class StoreViewModel(private val storeKey: String) : ViewModel() {

    // key == null stands for "All"
    data class Chip(val key: String?, val label: String)

    sealed class Source {
        object Loading : Source()
        data class Error(val message: String) : Source()
        data class Ready(val items: List<BrowseItem>) : Source()
    }

    data class UiState(
        val storeName: String,
        val restaurant: FoodRestaurantResponse?,
        val categories: List<Chip>,
        val category: String?,
        val items: List<BrowseItem>,
        val loading: Boolean,
        val error: String?
    )

    private val catalog = AppContainer.foodCatalog
    private val guestCart = AppContainer.guestCart
    private val isInRoom = storeKey == Cart.IN_ROOM_KEY

    // The picker has already loaded the restaurants, so the header is known before the menu arrives
    private val restaurant = MutableStateFlow(catalog.restaurants.value.firstOrNull { it.restaurantKey == storeKey })
    private val source = MutableStateFlow<Source>(Source.Loading)
    private val category = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(source, category, restaurant, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildState(Source.Loading, null, restaurant.value))

    val quantities: StateFlow<Map<String, Int>> = guestCart.quantities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val cart: StateFlow<Cart?> = guestCart.cart
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val placedOrderCount: StateFlow<Int> = AppContainer.sessionOrders
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppContainer.sessionOrders.value.size)

    // Set when the guest adds an item while the cart holds another store's items
    private val _pendingSwitch = MutableStateFlow<BrowseItem?>(null)
    val pendingSwitch: StateFlow<BrowseItem?> = _pendingSwitch.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        if (isInRoom) {
            source.value = Source.Ready(AppContainer.productRepository.getAll().map { BrowseItem.Store(it) })
            return
        }
        source.value = Source.Loading
        loadJob = viewModelScope.launch {
            try {
                if (restaurant.value == null) {
                    catalog.refreshRestaurants()
                    restaurant.value = catalog.restaurants.value.firstOrNull { it.restaurantKey == storeKey }
                }
                val store = checkNotNull(restaurant.value) { "This restaurant is no longer available" }
                source.value = Source.Ready(catalog.menu(store).map { BrowseItem.Dish(store, it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                source.value = Source.Error(e.message ?: "Could not load the menu")
            }
        }
    }

    fun selectCategory(key: String?) {
        category.value = key
    }

    fun add(item: BrowseItem) {
        val current = cart.value
        if (current != null && current.storeKey != storeKey) {
            _pendingSwitch.value = item
        } else {
            addToCart(item)
        }
    }

    fun remove(item: BrowseItem) {
        when (item) {
            is BrowseItem.Store -> guestCart.decrementStore(item.product.id)
            is BrowseItem.Dish -> guestCart.decrementDish(item.key)
        }
    }

    fun confirmSwitch() {
        val item = _pendingSwitch.value ?: return
        _pendingSwitch.value = null
        viewModelScope.launch {
            guestCart.clear()
            addToCart(item)
        }
    }

    fun keepCart() {
        _pendingSwitch.value = null
    }

    fun addStore(product: UiProduct) = guestCart.addStore(product)

    fun decrementStore(productId: String) = guestCart.decrementStore(productId)

    fun addDish(line: FoodCartLine) = guestCart.addDish(line.restaurant, line.item)

    fun decrementDish(line: FoodCartLine) = guestCart.decrementDish(line.key)

    private fun addToCart(item: BrowseItem) {
        when (item) {
            is BrowseItem.Store -> guestCart.addStore(item.product)
            is BrowseItem.Dish -> guestCart.addDish(item.restaurant, item.item)
        }
    }

    private fun buildState(source: Source, selected: String?, restaurant: FoodRestaurantResponse?): UiState {
        val ready = source as? Source.Ready
        val items = ready?.items.orEmpty()
        val categoryKeys = items.map { it.category }.distinct().let { keys ->
            if (isInRoom) keys.sortedBy { STORE_CATEGORY_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
            else keys
        }
        val category = selected?.takeIf { it in categoryKeys }
        return UiState(
            storeName = restaurant?.name ?: Cart.IN_ROOM_NAME,
            restaurant = restaurant,
            categories = listOf(Chip(null, "All")) + categoryKeys.map {
                Chip(it, if (isInRoom) storeCategoryLabel(it) else it)
            },
            category = category,
            items = items.filter { category == null || it.category == category },
            loading = source is Source.Loading,
            error = (source as? Source.Error)?.message
        )
    }

    private companion object {
        val STORE_CATEGORY_ORDER =
            listOf("FOOD", "BEVERAGE", "HOT_DRINK", "SNACK", "ALCOHOL", "PERSONAL_CARE", "PHARMACY")

        fun storeCategoryLabel(category: String): String = when (category) {
            "FOOD" -> "Food"
            "BEVERAGE" -> "Beverages"
            "HOT_DRINK" -> "Hot Drinks"
            "SNACK" -> "Snacks"
            "ALCOHOL" -> "Alcohol"
            "PERSONAL_CARE" -> "Personal Care"
            "PHARMACY" -> "Pharmacy"
            else -> category
        }
    }
}
