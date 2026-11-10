package net.kustax.opendelivery.model

import net.kustax.opendelivery.data.response.FoodMenuItemResponse
import net.kustax.opendelivery.data.response.FoodRestaurantResponse

// One orderable entry of the unified browse grid: an in-room store product or a restaurant dish.
sealed class BrowseItem {
    abstract val key: String
    abstract val name: String
    abstract val description: String
    abstract val priceCents: Long
    abstract val category: String
    abstract val imageUrl: String?
    open val allergens: List<String> get() = emptyList()

    data class Store(val product: UiProduct) : BrowseItem() {
        override val key: String get() = storeItemKey(product.id)
        override val name: String get() = product.name
        override val description: String get() = product.description
        override val priceCents: Long get() = product.priceCents
        override val category: String get() = product.category
        override val imageUrl: String? get() = product.imageUrl
        override val allergens: List<String> get() = product.allergenTags
    }

    data class Dish(val restaurant: FoodRestaurantResponse, val item: FoodMenuItemResponse) : BrowseItem() {
        override val key: String get() = dishItemKey(restaurant, item.id)
        override val name: String get() = item.name
        override val description: String get() = item.description
        override val priceCents: Long get() = item.priceCents
        override val category: String get() = item.category
        override val imageUrl: String? get() = null
    }
}

fun storeItemKey(productId: String): String = "store/$productId"

fun dishItemKey(restaurant: FoodRestaurantResponse, itemId: String): String =
    "${restaurant.restaurantKey}/$itemId"

// Identifies a restaurant across platforms; also used as the cart group key and navigation argument
val FoodRestaurantResponse.restaurantKey: String get() = "$platform:$id"
