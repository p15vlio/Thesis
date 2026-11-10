package net.kustax.opendelivery.ui.screen.store

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import net.kustax.opendelivery.data.response.FoodRestaurantResponse
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.navigation.Screen
import net.kustax.opendelivery.navigation.navigateToIdle
import net.kustax.opendelivery.ui.components.AdminPinDialog
import net.kustax.opendelivery.ui.components.BrowseItemCard
import net.kustax.opendelivery.ui.components.GuestDeskTopBar
import net.kustax.opendelivery.ui.components.InactivityWrapper
import net.kustax.opendelivery.ui.components.PlatformBadge
import net.kustax.opendelivery.ui.components.formatEuro
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted

// One store (the in-room store or a single restaurant): its categories and products next to the cart.
@Composable
fun StoreScreen(navController: NavController, storeKey: String) {
    val vm: StoreViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StoreViewModel(storeKey) as T
        }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val quantities by vm.quantities.collectAsStateWithLifecycle()
    val cart by vm.cart.collectAsStateWithLifecycle()
    val placedOrderCount by vm.placedOrderCount.collectAsStateWithLifecycle()
    val pendingSwitch by vm.pendingSwitch.collectAsStateWithLifecycle()
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

    pendingSwitch?.let {
        AlertDialog(
            onDismissRequest = vm::keepCart,
            title = { Text("Start a new order?") },
            text = {
                Text(
                    "Your cart has items from ${cart?.storeName}. " +
                        "Clear it and start a new order from ${state.storeName}?",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmSwitch) { Text("Clear & add") }
            },
            dismissButton = {
                TextButton(onClick = vm::keepCart) { Text("Keep my cart") }
            }
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
                    title = state.storeName,
                    showBack = true,
                    showCart = false,
                    onBack = { navController.popBackStack() },
                    onTrackClick = if (placedOrderCount > 0) {
                        { navController.navigate(Screen.OrderTracking.route) { launchSingleTop = true } }
                    } else null,
                    onAdminGesture = { showAdminDialog = true }
                )
            },
            containerColor = Background
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    state.restaurant?.let { RestaurantInfoRow(it) }
                    if (state.categories.size > 1) {
                        CategoryChips(
                            chips = state.categories,
                            selected = state.category,
                            onSelect = vm::selectCategory
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.loading -> CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = AmberPrimary
                            )

                            state.error != null -> ErrorContent(
                                message = state.error.orEmpty(),
                                onRetry = vm::load,
                                modifier = Modifier.align(Alignment.Center)
                            )

                            state.items.isEmpty() -> Text(
                                text = "Nothing available here right now",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnBackgroundMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )

                            else -> LazyVerticalGrid(
                                columns = GridCells.Adaptive(250.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.items, key = { it.key }) { item ->
                                    BrowseItemCard(
                                        item = item,
                                        quantity = quantities[item.key] ?: 0,
                                        onAddOne = { vm.add(item) },
                                        onRemoveOne = { vm.remove(item) }
                                    )
                                }
                            }
                        }
                    }
                }

                CartPanel(
                    cart = cart,
                    onAddStore = vm::addStore,
                    onDecrementStore = vm::decrementStore,
                    onAddDish = vm::addDish,
                    onDecrementDish = vm::decrementDish,
                    onCheckout = { navController.navigate(Screen.Payment.route) },
                    modifier = Modifier
                        .width(CART_PANEL_WIDTH)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun RestaurantInfoRow(restaurant: FoodRestaurantResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlatformBadge(restaurant.platform)
        Text(
            text = buildString {
                append(restaurant.cuisine)
                append("  ·  ★ ${"%.1f".format(restaurant.rating)}")
                append("  ·  ${restaurant.etaMinutes} min")
                append("  ·  ")
                append(if (restaurant.deliveryFeeCents == 0L) "Free delivery" else "Delivery ${formatEuro(restaurant.deliveryFeeCents)}")
                restaurant.minOrderCents?.takeIf { it > 0 }?.let { append("  ·  Min. order ${formatEuro(it)}") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = OnBackgroundMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryChips(
    chips: List<StoreViewModel.Chip>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips, key = { it.key ?: "" }) { chip ->
            FilterChip(
                selected = chip.key == selected,
                onClick = { onSelect(chip.key) },
                label = { Text(chip.label, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.height(40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberPrimary,
                    selectedLabelColor = Color.Black,
                    labelColor = OnBackground
                )
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = OnBackgroundMuted,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
        ) {
            Text("Try again", color = Color.Black)
        }
    }
}
