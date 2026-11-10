package net.kustax.opendelivery.web.util

import net.kustax.opendelivery.data.response.OrderResponse

fun String.toPlatformLabel(): String = when (this) {
    "EFOOD" -> "efood"
    "WOLT" -> "Wolt"
    "BOX" -> "Box"
    else -> toLabel()
}

// "Food delivery · efood · Restaurant" for platform orders, the plain type otherwise
fun OrderResponse.typeLabel(): String =
    if (type == "FOOD_DELIVERY") {
        listOfNotNull("Food delivery", externalPlatform?.toPlatformLabel(), restaurantName).joinToString(" · ")
    } else {
        type.toLabel()
    }

// The delivery platform advances FOOD_DELIVERY orders itself, so cancelling is the only manual action left
fun OrderResponse.ownerActions(nextStatuses: List<String>): List<String> =
    if (type == "FOOD_DELIVERY") nextStatuses.filter { it == "CANCELLED" } else nextStatuses
