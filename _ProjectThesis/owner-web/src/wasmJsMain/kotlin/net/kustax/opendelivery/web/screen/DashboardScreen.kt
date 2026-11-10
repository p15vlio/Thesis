package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import net.kustax.opendelivery.data.response.DashboardSummaryResponse
import net.kustax.opendelivery.data.response.OrderResponse
import net.kustax.opendelivery.data.response.PropertyResponse
import net.kustax.opendelivery.web.util.rethrowCancellation
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.LoadingState
import net.kustax.opendelivery.web.ui.StatusChip
import net.kustax.opendelivery.web.util.formatEuros

@Composable
fun DashboardScreen(
    client: ApiClient,
    onNavigateToOrders: (propertyId: String, propertyName: String) -> Unit,
    onNavigateToProducts: () -> Unit = {},
    onNavigateToAllOrders: () -> Unit = {}
) {
    var summary by remember { mutableStateOf<DashboardSummaryResponse?>(null) }
    var properties by remember { mutableStateOf<List<PropertyResponse>?>(null) }
    var recentOrders by remember { mutableStateOf<List<Pair<OrderResponse, String>>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            summary = client.getDashboardSummary()
            properties = client.listProperties()
            val props = properties ?: return@LaunchedEffect
            val pairs = props.flatMap { prop ->
                runCatching {
                    client.listOrders(prop.id, null).map { it to prop.name }
                }.rethrowCancellation().getOrElse { emptyList() }
            }.sortedByDescending { it.first.createdAt }.take(5)
            recentOrders = pairs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    if (summary == null && properties == null && errorMessage == null) {
        LoadingState()
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Text(
                "Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        errorMessage?.let {
            item {
                ErrorBanner(message = it, onDismiss = { errorMessage = null }, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        summary?.let { s ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Active Properties", s.activeProperties.toString(), Modifier.weight(1f))
                    StatCard("Active Devices", s.activeDevices.toString(), Modifier.weight(1f))
                    StatCard("Pending Orders", s.pendingOrders.toString(), Modifier.weight(1f), accent = Color(0xFFF59E0B))
                    StatCard("Preparing", s.preparingOrders.toString(), Modifier.weight(1f), accent = Color(0xFF8B5CF6))
                    StatCard("Out for Delivery", s.outForDeliveryOrders.toString(), Modifier.weight(1f), accent = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToProducts,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Product")
                }
                OutlinedButton(
                    onClick = onNavigateToAllOrders,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("All Orders")
                }
            }
        }

        val loadedProperties = properties
        if (!loadedProperties.isNullOrEmpty()) {
            item {
                Text(
                    "Properties",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    items(loadedProperties) { property ->
                        OutlinedButton(
                            onClick = { onNavigateToOrders(property.id, property.name) },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(property.name)
                        }
                    }
                }
            }
        }

        if (!recentOrders.isNullOrEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Recent Orders", style = MaterialTheme.typography.titleMedium)
                        recentOrders!!.forEachIndexed { index, (order, propertyName) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "#${order.id.takeLast(8).uppercase()}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        propertyName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatusChip(order.status)
                                    Text(
                                        formatEuros(order.totalAmount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (index < recentOrders!!.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val accentColor = accent ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColor
                )
            }
        }
    }
}
