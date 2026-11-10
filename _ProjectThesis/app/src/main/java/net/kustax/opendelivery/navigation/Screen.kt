package net.kustax.opendelivery.navigation

sealed class Screen(val route: String) {
    object Registration : Screen("registration")
    object Idle : Screen("idle")
    object Home : Screen("home")
    object Stores : Screen("stores")
    object Info : Screen("info")
    object Payment : Screen("payment")
    object OrderConfirmation : Screen("order_confirmation")
    object OrderTracking : Screen("order_tracking")

    // One store: the in-room store or a single restaurant
    object Store : Screen("store/{storeKey}") {
        fun createRoute(storeKey: String) = "store/${android.net.Uri.encode(storeKey)}"
        const val ARG_STORE_KEY = "storeKey"
    }
}
