package net.kustax.opendelivery.domain.port

import net.kustax.opendelivery.domain.enum.OrderStatus

// Callback a food delivery platform invokes when it changes the state of an order it accepted
// (the equivalent of the platform's status webhook). etaMinutes is the platform's current estimate.
interface FoodOrderEventListener {
    suspend fun onStatusChanged(schemaName: String, orderId: String, newStatus: OrderStatus, etaMinutes: Int?)
}
