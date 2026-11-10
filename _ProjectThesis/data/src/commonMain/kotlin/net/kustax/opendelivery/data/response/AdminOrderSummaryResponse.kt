package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class AdminOrderSummaryResponse(
    val totalActiveOrders: Int,
    val countsByStatus: Map<String, Int>
)
