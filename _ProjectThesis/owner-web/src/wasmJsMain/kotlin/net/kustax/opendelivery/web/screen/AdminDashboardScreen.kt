package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import net.kustax.opendelivery.data.response.AdminOrderSummaryResponse
import net.kustax.opendelivery.data.response.AuditLogResponse
import net.kustax.opendelivery.data.response.OwnerWithStatsResponse
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.ActiveBadge
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.util.formatTimestamp
import net.kustax.opendelivery.web.util.toLabel

@Composable
fun AdminDashboardScreen(client: ApiClient) {
    var ownerStats by remember { mutableStateOf(emptyList<OwnerWithStatsResponse>()) }
    var recentAuditLogs by remember { mutableStateOf(emptyList<AuditLogResponse>()) }
    var orderSummary by remember { mutableStateOf<AdminOrderSummaryResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            ownerStats = client.getOwnerStats()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    LaunchedEffect(Unit) {
        try {
            recentAuditLogs = client.listAuditLogs(limit = 10, offset = 0L)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        try {
            orderSummary = client.getAdminOrderSummary()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    val totalOwners = ownerStats.size
    val activeOwners = ownerStats.count { it.owner.isActive }
    val inactiveOwners = ownerStats.count { !it.owner.isActive }
    val totalProperties = ownerStats.sumOf { it.propertyCount }
    val totalDevices = ownerStats.sumOf { it.deviceCount }
    val recentOwners = ownerStats.sortedByDescending { it.owner.createdAt }.take(5)

    val top5ByProperties = ownerStats.sortedByDescending { it.propertyCount }.take(5)
    val barColor = Color(0xFF1E3A5F)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
        }

        errorMessage?.let {
            item {
                ErrorBanner(message = it, onDismiss = { errorMessage = null })
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total Owners", totalOwners.toString(), Modifier.weight(1f))
                StatCard("Active", activeOwners.toString(), Modifier.weight(1f), accent = MaterialTheme.colorScheme.tertiary)
                StatCard("Inactive", inactiveOwners.toString(), Modifier.weight(1f), accent = MaterialTheme.colorScheme.error)
                StatCard("Properties", totalProperties.toString(), Modifier.weight(1f))
                StatCard("Devices", totalDevices.toString(), Modifier.weight(1f))
            }
        }

        orderSummary?.let { summary ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active Orders", style = MaterialTheme.typography.titleSmall)
                        Text(
                            summary.totalActiveOrders.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (summary.countsByStatus.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                summary.countsByStatus.forEach { (status, count) ->
                                    Column {
                                        Text(
                                            count.toString(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            status.toLabel(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (top5ByProperties.isNotEmpty()) {
            item {
                val textMeasurer = rememberTextMeasurer()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Properties by Owner (Top 5)", style = MaterialTheme.typography.titleSmall)
                        val maxCount = top5ByProperties.maxOf { it.propertyCount }.coerceAtLeast(1)
                        // Extra 30.dp height to leave room for X-axis labels
                        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                            val barCount = top5ByProperties.size
                            val paddingLeft = 8f
                            val spacing = 8f
                            val totalSpacing = spacing * (barCount + 1)
                            val barWidth = (size.width - totalSpacing - paddingLeft) / barCount
                            val labelAreaHeight = 30.dp.toPx()
                            val chartHeight = size.height - labelAreaHeight
                            val axisY = chartHeight - 16f

                            drawLine(
                                color = Color.Gray,
                                start = Offset(0f, axisY),
                                end = Offset(size.width, axisY),
                                strokeWidth = 1f
                            )

                            top5ByProperties.forEachIndexed { index, item ->
                                val x = paddingLeft + spacing + index * (barWidth + spacing)
                                val barHeight = ((item.propertyCount.toFloat() / maxCount) * (axisY - 8f)).coerceAtLeast(2f)
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset(x, axisY - barHeight),
                                    size = Size(barWidth, barHeight)
                                )

                                // X-axis label centered under each bar
                                val labelText = item.owner.displayName.take(9)
                                val measured = textMeasurer.measure(
                                    labelText,
                                    style = TextStyle(fontSize = 9.sp, color = Color.Gray)
                                )
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(
                                        x = x + barWidth / 2f - measured.size.width / 2f,
                                        y = chartHeight + 6.dp.toPx()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Recent Owners", style = MaterialTheme.typography.titleMedium)
        }

        if (recentOwners.isEmpty()) {
            item {
                Text(
                    "No owners yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(recentOwners) { item ->
                val owner = item.owner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(owner.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                owner.contactEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${item.propertyCount} properties · ${item.deviceCount} devices · ${item.productCount} products",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActiveBadge(owner.isActive)
                            Text(
                                formatTimestamp(owner.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (recentAuditLogs.isNotEmpty()) {
            item {
                Text("Recent Activity", style = MaterialTheme.typography.titleMedium)
            }
            items(recentAuditLogs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "${log.actorRole.toLabel()} · ${log.action}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                log.targetType + (log.targetId?.let { " ($it)" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatTimestamp(log.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
