package net.kustax.opendelivery.ui.screen.confirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.model.UiOrder
import net.kustax.opendelivery.navigation.Screen
import net.kustax.opendelivery.navigation.returnToHome
import net.kustax.opendelivery.ui.components.PlatformBadge
import net.kustax.opendelivery.ui.components.formatEuro
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.OnBackgroundSubtle
import net.kustax.opendelivery.ui.theme.SuccessGreen
import net.kustax.opendelivery.ui.theme.SurfaceDark

// Shown right after payment: the order that was just placed
@Composable
fun OrderConfirmationScreen(navController: NavController) {
    val orders by AppContainer.sessionOrders.collectAsStateWithLifecycle()
    val order = orders.lastOrNull() ?: return

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(SuccessGreen.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "Order placed!",
                style = MaterialTheme.typography.headlineMedium,
                color = OnBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (!order.guestEmail.isNullOrBlank()) {
                Text(
                    text = "A receipt will be sent to ${order.guestEmail}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnBackgroundMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = {
                    navController.navigate(Screen.OrderTracking.route) { launchSingleTop = true }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
            ) {
                Text(
                    text = "Track My Order",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
            }

            OutlinedButton(
                onClick = { navController.returnToHome() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnBackgroundMuted)
            ) {
                Text("Back to Home", style = MaterialTheme.typography.bodyLarge)
            }
        }

        OrderCard(
            order = order,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        )
    }
}

@Composable
private fun OrderCard(order: UiOrder, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                order.externalPlatform?.let { PlatformBadge(it) }
                Text(
                    text = order.restaurantName ?: "In-room store",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Order #${order.shortCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = AmberPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${item.quantity}× ${item.product.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnBackground,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = formatEuro(item.subtotalCents),
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnBackgroundMuted
                        )
                    }
                }
                if (order.isFoodDelivery) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delivery fee", style = MaterialTheme.typography.bodyLarge, color = OnBackgroundMuted)
                        Text(
                            text = formatEuro(order.deliveryFeeCents),
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnBackgroundMuted
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleMedium, color = OnBackground)
                Text(formatEuro(order.totalCents), style = MaterialTheme.typography.titleMedium, color = AmberPrimary)
            }
            if (!order.notes.isNullOrBlank()) {
                Text(
                    text = "Note: ${order.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnBackgroundSubtle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
