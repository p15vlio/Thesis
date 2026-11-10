package net.kustax.opendelivery.ui.screen.home

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.navigation.Screen
import net.kustax.opendelivery.navigation.navigateToIdle
import net.kustax.opendelivery.ui.components.AdminPinDialog
import net.kustax.opendelivery.ui.components.GuestDeskTopBar
import net.kustax.opendelivery.ui.components.InactivityWrapper
import net.kustax.opendelivery.ui.components.OfflineBanner
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.OnBackgroundSubtle

@Composable
fun HomeScreen(navController: NavController) {
    val vm: HomeViewModel = viewModel()
    val cartCount by vm.cartItemCount.collectAsStateWithLifecycle()
    val isCatalogEmpty by vm.isCatalogEmpty.collectAsStateWithLifecycle()
    val cartStoreKey by vm.cartStoreKey.collectAsStateWithLifecycle()
    val hasOrders by vm.hasOrders.collectAsStateWithLifecycle()
    val property by vm.property.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAdminDialog by remember { mutableStateOf(false) }

    if (showAdminDialog) {
        AdminPinDialog(
            onConfirm = {
                showAdminDialog = false
                (context as? Activity)?.let { activity ->
                    KioskManager(activity).stopKiosk()
                    activity.finish()
                }
            },
            onDismiss = { showAdminDialog = false }
        )
    }

    InactivityWrapper(
        onTimeout = {
            navController.navigateToIdle()
        }
    ) {
        Scaffold(
            topBar = {
                GuestDeskTopBar(
                    title = property?.name.orEmpty(),
                    cartItemCount = cartCount,
                    showCart = true,
                    showInfo = true,
                    onCartClick = {
                        navController.navigate(cartStoreKey?.let { Screen.Store.createRoute(it) } ?: Screen.Stores.route)
                    },
                    onInfoClick = { navController.navigate(Screen.Info.route) },
                    onAdminGesture = { showAdminDialog = true }
                )
            },
            containerColor = Background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = vm.greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            color = OnBackgroundMuted
                        )
                        Text(
                            text = property?.name.orEmpty(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = OnBackground
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { navController.navigate(Screen.Stores.route) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                        ) {
                            Text(
                                text = "Order Now",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.Black
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.OrderTracking.route) { launchSingleTop = true } },
                            enabled = hasOrders,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (hasOrders) OnBackgroundMuted else OnBackgroundSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = OnBackground,
                                disabledContentColor = OnBackgroundSubtle
                            )
                        ) {
                            Text("Track My Order")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (highlights.isNotEmpty()) {
                            Text(
                                text = "Today's Highlights",
                                style = MaterialTheme.typography.titleLarge,
                                color = OnBackground,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            highlights.chunked(2).forEach { rowCards ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowCards.forEach { highlight ->
                                        HighlightCard(
                                            highlight,
                                            onClick = { navController.navigate(Screen.Store.createRoute(highlight.storeKey)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowCards.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                OfflineBanner(
                    contactPhone = property?.contactPhone.orEmpty(),
                    visible = isCatalogEmpty,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun HighlightCard(banner: Highlight, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = banner.color)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Text(
                    text = banner.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = banner.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
