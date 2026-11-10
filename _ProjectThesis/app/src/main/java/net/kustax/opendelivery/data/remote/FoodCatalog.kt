package net.kustax.opendelivery.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import net.kustax.opendelivery.data.local.DevicePreferences
import net.kustax.opendelivery.data.response.FoodMenuItemResponse
import net.kustax.opendelivery.data.response.FoodRestaurantResponse
import net.kustax.opendelivery.model.restaurantKey
import java.util.concurrent.ConcurrentHashMap

// Restaurants available at this property plus a short-lived cache of their menus, shared by all screens.
class FoodCatalog(
    private val apiClient: AppApiClient,
    private val devicePreferences: DevicePreferences
) {
    private class CachedMenu(val loadedAt: Long, val items: List<FoodMenuItemResponse>)

    private val _restaurants = MutableStateFlow<List<FoodRestaurantResponse>>(emptyList())
    val restaurants: StateFlow<List<FoodRestaurantResponse>> = _restaurants.asStateFlow()

    private val menuCache = ConcurrentHashMap<String, CachedMenu>()

    // A failed refresh leaves no restaurants, so the food sources simply disappear from the browse screen
    suspend fun refreshRestaurants() {
        _restaurants.value = try {
            val state = devicePreferences.registrationState.first()
            if (state.isRegistered) apiClient.getFoodRestaurants(state.serverUrl, state.token) else emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun menu(restaurant: FoodRestaurantResponse): List<FoodMenuItemResponse> {
        val now = System.currentTimeMillis()
        menuCache[restaurant.restaurantKey]?.takeIf { now - it.loadedAt < MENU_TTL_MS }?.let { return it.items }
        val state = devicePreferences.registrationState.first()
        val items = apiClient.getFoodMenu(state.serverUrl, state.token, restaurant.platform, restaurant.id)
        menuCache[restaurant.restaurantKey] = CachedMenu(now, items)
        return items
    }

    private companion object {
        const val MENU_TTL_MS = 10 * 60 * 1000L
    }
}
