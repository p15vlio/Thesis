package net.kustax.opendelivery.model

data class UiOrder(
    val id: String,
    val status: String,
    val items: List<CartItem>,
    val totalCents: Long,
    val guestEmail: String?,
    val notes: String?,
    val createdAt: Long,
    val type: String = "IN_ROOM_STORE",
    val externalPlatform: String? = null,
    val externalOrderId: String? = null,
    val restaurantName: String? = null,
    val deliveryFeeCents: Long = 0,
    val estimatedDeliveryAt: Long? = null
) {
    val isFoodDelivery: Boolean get() = type == "FOOD_DELIVERY"
    // Short, easy to read out order code shown to guests
    val shortCode: String get() = id.take(8).uppercase()
    val isFinal: Boolean get() = status == "DELIVERED" || status == "CANCELLED"
    // The guest may cancel (and is refunded) until preparation starts; same rule as the backend
    val isGuestCancellable: Boolean get() = status == "PENDING" || status == "CONFIRMED"
}
