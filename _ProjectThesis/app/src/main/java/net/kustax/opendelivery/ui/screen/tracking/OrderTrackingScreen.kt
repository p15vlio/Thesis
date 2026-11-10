package net.kustax.opendelivery.ui.screen.tracking

import android.app.Activity
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.model.UiOrder
import net.kustax.opendelivery.navigation.navigateToIdle
import net.kustax.opendelivery.navigation.returnToHome
import net.kustax.opendelivery.ui.components.AdminPinDialog
import net.kustax.opendelivery.ui.components.GuestDeskTopBar
import net.kustax.opendelivery.ui.components.InactivityWrapper
import net.kustax.opendelivery.ui.components.PlatformBadge
import net.kustax.opendelivery.ui.components.formatEuro
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.ErrorRed
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.StepActive
import net.kustax.opendelivery.ui.theme.StepComplete
import net.kustax.opendelivery.ui.theme.StepPending
import net.kustax.opendelivery.ui.theme.SuccessGreen
import net.kustax.opendelivery.ui.theme.SurfaceDark

// Store and food orders share one set of steps so their progress lines up side by side.
// A PENDING store order is still waiting for the first step.
private val TRACKING_STEPS = listOf(
    "CONFIRMED" to "Confirmed",
    "PREPARING" to "Preparing",
    "OUT_FOR_DELIVERY" to "On the way",
    "DELIVERED" to "Delivered"
)

private const val MAX_LISTED_ITEMS = 3

@Composable
fun OrderTrackingScreen(navController: NavController) {
    val vm: OrderTrackingViewModel = viewModel()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAdminDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var orderToCancel by remember { mutableStateOf<UiOrder?>(null) }
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearFeedback()
        }
    }
    val property by AppContainer.propertyRepository.property.collectAsStateWithLifecycle()

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

    orderToCancel?.let { order ->
        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            title = { Text("Cancel this order?") },
            text = {
                Text(
                    if (order.status == "PENDING") "The order has not been paid yet and will be cancelled."
                    else "The order will be cancelled and ${formatEuro(order.totalCents)} refunded to your card."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancelOrder(order.id)
                    orderToCancel = null
                }) { Text("Cancel order", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { orderToCancel = null }) { Text("Keep order") }
            }
        )
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            title = { Text("Need Help?") },
            text = {
                Text(
                    property?.contactPhone?.takeIf { it.isNotBlank() }?.let { "Call us at $it" }
                        ?: "Please ask the front desk for help.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = { showContactDialog = false }) { Text("OK") }
            }
        )
    }

    InactivityWrapper(
        onTimeout = { navController.navigateToIdle() }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                GuestDeskTopBar(
                    title = "Order Status",
                    showBack = true,
                    showCart = false,
                    onBack = { navController.popBackStack() },
                    onAdminGesture = { showAdminDialog = true }
                )
            },
            containerColor = Background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (orders.isEmpty()) {
                        Text(
                            text = "No orders yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = OnBackgroundMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        // Cards share the available height so the screen is filled; more than two rows scroll
                        val columns = minOf(orders.size, 2)
                        val rows = (orders.size + columns - 1) / columns
                        val cardHeight = ((maxHeight - 32.dp - 12.dp * (rows - 1)) / rows).coerceIn(250.dp, 340.dp)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(orders, key = { it.id }) { order ->
                                OrderTrackingCard(
                                    order = order,
                                    onCancel = { orderToCancel = order },
                                    modifier = Modifier.height(cardHeight)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showContactDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp)
                        )
                        Text("Need help? Contact us")
                    }
                    OutlinedButton(
                        onClick = {
                            navController.returnToHome()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Return to Home")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderTrackingCard(order: UiOrder, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    order.externalPlatform?.let { PlatformBadge(it) }
                    Text(
                        text = order.restaurantName ?: "In-room store",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (order.isGuestCancellable) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Text("Cancel order")
                        }
                    }
                }
                Text(
                    text = buildString {
                        append("Order #${order.shortCode}")
                        order.externalOrderId?.let { append("  ·  Platform order #$it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnBackgroundMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusBlock(order)
                OrderItemsSummary(order)
            }

            val currentIdx = when (order.status) {
                "PENDING" -> 0
                "DELIVERED" -> TRACKING_STEPS.size
                else -> TRACKING_STEPS.indexOfFirst { it.first == order.status }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TRACKING_STEPS.forEachIndexed { index, (_, label) ->
                    TrackingStep(
                        label = label,
                        index = index,
                        currentIdx = currentIdx,
                        isLast = index == TRACKING_STEPS.lastIndex,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// What is happening with the order right now, plus the countdown for food orders
@Composable
private fun StatusBlock(order: UiOrder) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(order.estimatedDeliveryAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val (message, color) = when (order.status) {
        "PENDING" -> "Waiting for confirmation" to OnBackgroundMuted
        "CONFIRMED" -> "Your order is confirmed" to AmberPrimary
        "PREPARING" -> "Your order is being prepared" to AmberPrimary
        "OUT_FOR_DELIVERY" -> (if (order.isFoodDelivery) "Your order is on the way" else "On its way to your room") to AmberPrimary
        "DELIVERED" -> (if (order.isFoodDelivery) "Delivered. Enjoy your meal!" else "Delivered to your room") to SuccessGreen
        "CANCELLED" -> "This order was cancelled" to ErrorRed
        else -> order.status to OnBackgroundMuted
    }
    Text(text = message, style = MaterialTheme.typography.titleLarge, color = color)

    val eta = order.estimatedDeliveryAt
    if (order.isFoodDelivery && eta != null && !order.isFinal) {
        val remainingSeconds = ((eta - now) / 1000L).coerceAtLeast(0L)
        Text(
            text = if (remainingSeconds == 0L) "Arriving any moment"
            else "Estimated arrival in %d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
            style = MaterialTheme.typography.titleMedium,
            color = AmberPrimary
        )
    }
}

@Composable
private fun OrderItemsSummary(order: UiOrder) {
    val listed = order.items.take(MAX_LISTED_ITEMS)
    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listed.forEach { item ->
            Text(
                text = "${item.quantity}× ${item.product.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackgroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (order.items.size > listed.size) {
            Text(
                text = "+ ${order.items.size - listed.size} more",
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackgroundMuted
            )
        }
        Text(
            text = "Total ${formatEuro(order.totalCents)}",
            style = MaterialTheme.typography.titleMedium,
            color = OnBackground,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun TrackingStep(
    label: String,
    index: Int,
    currentIdx: Int,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val isComplete = currentIdx > index
    val isActive = currentIdx == index

    val infiniteTransition = rememberInfiniteTransition(label = "activeStep")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stepPulse"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            // Connector halves: left one reaches back to the previous step, right one on to the next
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .align(Alignment.CenterVertically)
                        .background(
                            when {
                                index == 0 -> Color.Transparent
                                currentIdx >= index -> StepComplete
                                else -> StepPending
                            }
                        )
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .align(Alignment.CenterVertically)
                        .background(
                            when {
                                isLast -> Color.Transparent
                                currentIdx > index -> StepComplete
                                else -> StepPending
                            }
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .scale(if (isActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            isComplete -> StepComplete
                            isActive -> StepActive
                            else -> StepPending
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isComplete) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = when {
                isComplete -> SuccessGreen
                isActive -> AmberPrimary
                else -> OnBackgroundMuted
            },
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
