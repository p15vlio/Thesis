package net.kustax.opendelivery.data.remote

import net.kustax.opendelivery.data.response.OrderItemResponse
import net.kustax.opendelivery.data.response.OrderResponse
import net.kustax.opendelivery.model.CartItem
import net.kustax.opendelivery.model.UiOrder
import net.kustax.opendelivery.model.UiProduct

fun OrderResponse.toUiOrder(): UiOrder = UiOrder(
    id = id,
    status = status,
    items = items.map { it.toCartItem() },
    totalCents = totalAmount,
    guestEmail = guestEmail,
    notes = notes,
    createdAt = createdAt,
    type = type,
    externalPlatform = externalPlatform,
    externalOrderId = externalOrderId,
    restaurantName = restaurantName,
    deliveryFeeCents = deliveryFee,
    estimatedDeliveryAt = estimatedDeliveryAt
)

private fun OrderItemResponse.toCartItem(): CartItem = CartItem(
    product = UiProduct(
        id = productId,
        name = productName,
        description = "",
        category = "OTHER",
        priceCents = unitPrice,
        imageUrl = null,
        isAvailable = true
    ),
    quantity = quantity
)
