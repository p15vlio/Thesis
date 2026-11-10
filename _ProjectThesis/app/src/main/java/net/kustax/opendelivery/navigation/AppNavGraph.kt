package net.kustax.opendelivery.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.ui.screen.confirmation.OrderConfirmationScreen
import net.kustax.opendelivery.ui.screen.home.HomeScreen
import net.kustax.opendelivery.ui.screen.idle.IdleScreen
import net.kustax.opendelivery.ui.screen.info.InfoScreen
import net.kustax.opendelivery.ui.screen.payment.PaymentScreen
import net.kustax.opendelivery.ui.screen.registration.RegistrationScreen
import net.kustax.opendelivery.ui.screen.store.StoreScreen
import net.kustax.opendelivery.ui.screen.stores.StoresScreen
import net.kustax.opendelivery.ui.screen.tracking.OrderTrackingScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    val registrationState by AppContainer.devicePreferences.registrationState.collectAsState(initial = null)
    var previouslyRegistered by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(registrationState?.isRegistered) {
        val nowRegistered = registrationState?.isRegistered
        if (previouslyRegistered == true && nowRegistered == false) {
            navController.navigate(Screen.Registration.route) { popUpTo(navController.graph.id) { inclusive = true } }
        }
        if (nowRegistered != null) previouslyRegistered = nowRegistered
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Registration.route) {
            RegistrationScreen(navController = navController)
        }
        composable(Screen.Idle.route) {
            IdleScreen(navController = navController)
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Stores.route) {
            StoresScreen(navController = navController)
        }
        composable(
            route = Screen.Store.route,
            arguments = listOf(navArgument(Screen.Store.ARG_STORE_KEY) { type = NavType.StringType })
        ) { backStackEntry ->
            val storeKey = backStackEntry.arguments?.getString(Screen.Store.ARG_STORE_KEY) ?: ""
            StoreScreen(navController = navController, storeKey = storeKey)
        }
        composable(Screen.Payment.route) {
            PaymentScreen(navController = navController)
        }
        composable(Screen.OrderConfirmation.route) {
            OrderConfirmationScreen(navController = navController)
        }
        composable(Screen.OrderTracking.route) {
            OrderTrackingScreen(navController = navController)
        }
        composable(Screen.Info.route) {
            InfoScreen(navController = navController)
        }
    }
}
