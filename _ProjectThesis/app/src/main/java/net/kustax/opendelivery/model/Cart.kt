package net.kustax.opendelivery.model

import net.kustax.opendelivery.data.response.FoodRestaurantResponse

data class CartLine(val name: String, val quantity: Int, val subtotalCents: Long)

// The guest cart always holds items of exactly one store: the in-room store or a single restaurant.
sealed class Cart {
    abstract val storeKey: String
    abstract val storeName: String
    abstract val itemCount: Int
    abstract val itemsCents: Long
    abstract val summaryLines: List<CartLine>
    open val deliveryFeeCents: Long get() = 0L
    open val minOrderCents: Long get() = 0L

    val totalCents: Long get() = itemsCents + deliveryFeeCents
    val isPayable: Boolean get() = itemsCents >= minOrderCents

    data class InRoom(val items: List<CartItem>) : Cart() {
        override val storeKey: String get() = IN_ROOM_KEY
        override val storeName: String get() = IN_ROOM_NAME
        override val itemCount: Int get() = items.sumOf { it.quantity }
        override val itemsCents: Long get() = items.sumOf { it.subtotalCents }
        override val summaryLines: List<CartLine>
            get() = items.map { CartLine(it.product.name, it.quantity, it.subtotalCents) }
    }

    data class Restaurant(
        val restaurant: FoodRestaurantResponse,
        val lines: List<FoodCartLine>
    ) : Cart() {
        override val storeKey: String get() = restaurant.restaurantKey
        override val storeName: String get() = restaurant.name
        override val itemCount: Int get() = lines.sumOf { it.quantity }
        override val itemsCents: Long get() = lines.sumOf { it.subtotalCents }
        override val summaryLines: List<CartLine>
            get() = lines.map { CartLine(it.item.name, it.quantity, it.subtotalCents) }
        override val deliveryFeeCents: Long get() = restaurant.deliveryFeeCents
        override val minOrderCents: Long get() = restaurant.minOrderCents ?: 0L
    }

    companion object {
        const val IN_ROOM_KEY = "inroom"
        const val IN_ROOM_NAME = "In-room store"
    }
}
