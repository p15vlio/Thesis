package net.kustax.opendelivery.ui.screen.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.data.response.FoodRestaurantResponse

// Lists the places the guest can order from: the in-room store plus the restaurants of each platform
class StoresViewModel : ViewModel() {

    private val platformOrder = listOf("EFOOD", "WOLT", "BOX")

    private val _loading = MutableStateFlow(AppContainer.foodCatalog.restaurants.value.isEmpty())
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Restaurants per platform; platforms without a restaurant at this property do not appear
    val platforms: StateFlow<List<Pair<String, List<FoodRestaurantResponse>>>> = AppContainer.foodCatalog.restaurants
        .map { restaurants ->
            restaurants.groupBy { it.platform }.toList()
                .sortedBy { (platform, _) -> platformOrder.indexOf(platform).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Starts from what is already loaded and is refreshed below, so a catalog that was still empty when the
    // guest opened this screen (e.g. the backend was slow at startup) does not leave the tile at 0 items
    private val _inRoomItemCount = MutableStateFlow(AppContainer.productRepository.getAll().size)
    val inRoomItemCount: StateFlow<Int> = _inRoomItemCount.asStateFlow()

    init {
        viewModelScope.launch {
            AppContainer.foodCatalog.refreshRestaurants()
            _loading.value = false
        }
        viewModelScope.launch {
            AppContainer.refreshCatalogAndProperty()
            _inRoomItemCount.value = AppContainer.productRepository.getAll().size
        }
    }
}
