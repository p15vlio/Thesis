package net.kustax.opendelivery.web

sealed class Screen {
    object Login : Screen()
    object OwnerList : Screen()
    object Dashboard : Screen()
    object PropertyList : Screen()
    data class DeviceList(val propertyId: String, val propertyName: String) : Screen()
    object ProductList : Screen()
    object FoodDelivery : Screen()
    data class OrderList(val propertyId: String, val propertyName: String) : Screen()
    object AdminDashboard : Screen()
    object About : Screen()
    object Settings : Screen()
    object AuditLogs : Screen()
    data class AdminOwnerView(val ownerId: String, val schemaName: String, val displayName: String) : Screen()
    object AdminProperties : Screen()
    object AdminDevices : Screen()
    object AdminProducts : Screen()
    object OwnerOrders : Screen()
    object Help : Screen()
}
