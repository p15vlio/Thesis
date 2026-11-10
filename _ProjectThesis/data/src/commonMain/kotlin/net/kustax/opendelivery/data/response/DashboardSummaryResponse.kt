package net.kustax.opendelivery.data.response

import kotlinx.serialization.Serializable

@Serializable
data class DashboardSummaryResponse(
    val totalProperties: Int,
    val activeProperties: Int,
    val totalDevices: Int,
    val activeDevices: Int,
    val pendingOrders: Int,
    val confirmedOrders: Int,
    val preparingOrders: Int,
    val outForDeliveryOrders: Int
)
