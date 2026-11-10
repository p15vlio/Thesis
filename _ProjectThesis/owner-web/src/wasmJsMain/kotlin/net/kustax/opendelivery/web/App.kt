package net.kustax.opendelivery.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.auth.AuthSession
import net.kustax.opendelivery.web.auth.LoginScreen
import net.kustax.opendelivery.web.auth.SessionController
import net.kustax.opendelivery.web.screen.*
import net.kustax.opendelivery.web.ui.AppScaffold
import net.kustax.opendelivery.web.ui.OpenDeliveryTheme
import net.kustax.opendelivery.web.ui.SnackbarController

private fun AuthSession.defaultScreen(): Screen =
    if (role == "PLATFORM_ADMIN") Screen.AdminDashboard else Screen.Dashboard

@Composable
fun App() {
    OpenDeliveryTheme {
        var authSession by remember { mutableStateOf(AuthSession.load()) }
        var currentScreen by remember { mutableStateOf<Screen>(authSession?.defaultScreen() ?: Screen.Login) }
        val client = remember(authSession) { authSession?.let { ApiClient(ApiClient.BASE_URL, it.accessToken) } }

        fun onLogout() {
            AuthSession.clear()
            authSession = null
            currentScreen = Screen.Login
        }

        fun navigate(screen: Screen) {
            currentScreen = screen
        }

        val sessionExpired by SessionController.expired
        LaunchedEffect(sessionExpired) {
            if (sessionExpired) {
                AuthSession.clear()
                SnackbarController.show("Your session has expired. Please log in again.")
                authSession = null
                currentScreen = Screen.Login
                SessionController.reset()
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = currentScreen) {
                Screen.Login -> LoginScreen(
                    onSuccess = { session ->
                        session.save()
                        authSession = session
                        currentScreen = session.defaultScreen()
                    }
                )
                Screen.OwnerList -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    OwnerListScreen(client!!, onNavigate = ::navigate)
                }
                Screen.Dashboard -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    DashboardScreen(
                        client = client!!,
                        onNavigateToOrders = { propertyId, propertyName ->
                            navigate(Screen.OrderList(propertyId, propertyName))
                        },
                        onNavigateToProducts = { navigate(Screen.ProductList) },
                        onNavigateToAllOrders = { navigate(Screen.OwnerOrders) }
                    )
                }
                Screen.PropertyList -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    PropertyListScreen(
                        client = client!!,
                        onNavigateToDevices = { id, name -> navigate(Screen.DeviceList(id, name)) },
                        onNavigateToOrders = { id, name -> navigate(Screen.OrderList(id, name)) }
                    )
                }
                is Screen.DeviceList -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    DeviceListScreen(
                        client = client!!,
                        propertyId = screen.propertyId,
                        propertyName = screen.propertyName,
                        onBack = { navigate(Screen.PropertyList) }
                    )
                }
                Screen.ProductList -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    ProductListScreen(client!!)
                }
                Screen.FoodDelivery -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    FoodDeliveryScreen(client!!)
                }
                is Screen.OrderList -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    OrderListScreen(
                        client = client!!,
                        propertyId = screen.propertyId,
                        propertyName = screen.propertyName,
                        onBack = { navigate(Screen.PropertyList) }
                    )
                }
                Screen.AdminDashboard -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    AdminDashboardScreen(client!!)
                }
                Screen.About -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    AboutScreen(client = client)
                }
                Screen.Settings -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    SettingsScreen(client!!)
                }
                Screen.AuditLogs -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    AuditLogScreen(client = client!!, onNavigate = { currentScreen = it })
                }
                Screen.AdminProperties -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    AdminPropertiesScreen(client = client!!, onNavigate = { currentScreen = it })
                }
                Screen.AdminDevices -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    AdminDevicesScreen(client = client!!, onNavigate = { currentScreen = it })
                }
                Screen.AdminProducts -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    AdminProductsScreen(client = client!!, onNavigate = { currentScreen = it })
                }
                is Screen.AdminOwnerView -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    AdminOwnerViewScreen(
                        client = client!!,
                        ownerId = screen.ownerId,
                        schemaName = screen.schemaName,
                        displayName = screen.displayName,
                        onBack = { currentScreen = Screen.OwnerList }
                    )
                }
                Screen.OwnerOrders -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    OwnerOrdersScreen(client = client!!)
                }
                Screen.Help -> AppScaffold(authSession!!, currentScreen, ::navigate, ::onLogout) {
                    HelpScreen()
                }
            }
        }
    }
}
