package net.kustax.opendelivery.ui.screen.stores

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import net.kustax.opendelivery.data.response.FoodRestaurantResponse
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.model.Cart
import net.kustax.opendelivery.model.restaurantKey
import net.kustax.opendelivery.navigation.Screen
import net.kustax.opendelivery.navigation.navigateToIdle
import net.kustax.opendelivery.ui.components.AdminPinDialog
import net.kustax.opendelivery.ui.components.GuestDeskTopBar
import net.kustax.opendelivery.ui.components.InactivityWrapper
import net.kustax.opendelivery.ui.components.PlatformBadge
import net.kustax.opendelivery.ui.components.formatEuro
import net.kustax.opendelivery.ui.components.platformColor
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.OnBackgroundSubtle
import net.kustax.opendelivery.ui.theme.SurfaceDark

private const val CARDS_PER_ROW = 4
private val IN_ROOM_CARD_HEIGHT = 92.dp
private val SECTION_HEADER_HEIGHT = 30.dp

// Where to order from: the in-room store first, then the restaurants of each delivery platform
@Composable
fun StoresScreen(navController: NavController) {
    val vm: StoresViewModel = viewModel()
    val platforms by vm.platforms.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val inRoomItemCount by vm.inRoomItemCount.collectAsStateWithLifecycle()
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
                    title = "Where would you like to order from?",
                    showBack = true,
                    showCart = false,
                    onBack = { navController.popBackStack() },
                    onAdminGesture = { showAdminDialog = true }
                )
            },
            containerColor = Background
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Restaurants are laid out four per row. The rows share the height left after the in-room
                // card, so the screen is filled without clipping; it only scrolls if a platform has very many.
                val rows = platforms.sumOf { (_, restaurants) -> (restaurants.size + CARDS_PER_ROW - 1) / CARDS_PER_ROW }
                val chrome = 32.dp + IN_ROOM_CARD_HEIGHT + 14.dp + (SECTION_HEADER_HEIGHT + 22.dp) * platforms.size
                val cardHeight = ((maxHeight - chrome) / rows.coerceAtLeast(1)).coerceIn(100.dp, 140.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    InRoomStoreCard(
                        itemCount = inRoomItemCount,
                        onClick = { navController.navigate(Screen.Store.createRoute(Cart.IN_ROOM_KEY)) }
                    )

                    if (loading) {
                        Text(
                            text = "Looking for restaurants...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnBackgroundSubtle
                        )
                    }

                    platforms.forEach { (platform, restaurants) ->
                        PlatformSection(
                            platform = platform,
                            restaurants = restaurants,
                            cardHeight = cardHeight,
                            onSelect = { navController.navigate(Screen.Store.createRoute(it.restaurantKey)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InRoomStoreCard(itemCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IN_ROOM_CARD_HEIGHT)
            .background(AmberPrimary, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Storefront,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Cart.IN_ROOM_NAME,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black
            )
            Text(
                text = "${if (itemCount == 1) "1 item" else "$itemCount items"} from your host, brought to your room",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun PlatformSection(
    platform: String,
    restaurants: List<FoodRestaurantResponse>,
    cardHeight: Dp,
    onSelect: (FoodRestaurantResponse) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.height(SECTION_HEADER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlatformBadge(platform)
            Text(
                text = "${restaurants.size} ${if (restaurants.size == 1) "place" else "places"} deliver to you",
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackgroundMuted
            )
        }
        restaurants.chunked(CARDS_PER_ROW).forEach { rowRestaurants ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowRestaurants.forEach { restaurant ->
                    RestaurantCard(
                        restaurant = restaurant,
                        onClick = { onSelect(restaurant) },
                        modifier = Modifier
                            .weight(1f)
                            .height(cardHeight)
                    )
                }
                repeat(CARDS_PER_ROW - rowRestaurants.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RestaurantCard(restaurant: FoodRestaurantResponse, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxSize()
                    .background(platformColor(restaurant.platform))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${restaurant.cuisine}  ·  ★ ${"%.1f".format(restaurant.rating)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnBackgroundMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${restaurant.etaMinutes} min",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberPrimary
                        )
                        Text(
                            text = if (restaurant.deliveryFeeCents == 0L) "Free delivery" else "${formatEuro(restaurant.deliveryFeeCents)} delivery",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnBackgroundMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    restaurant.minOrderCents?.takeIf { it > 0 }?.let {
                        Text(
                            text = "Min. order ${formatEuro(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnBackgroundSubtle
                        )
                    }
                }
            }
        }
    }
}
