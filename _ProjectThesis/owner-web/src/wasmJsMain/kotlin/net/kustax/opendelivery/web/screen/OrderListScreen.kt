package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.response.OrderResponse
import net.kustax.opendelivery.data.response.PaymentResponse
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.BreadcrumbBar
import net.kustax.opendelivery.web.ui.EmptyState
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.LoadingState
import net.kustax.opendelivery.web.ui.PaymentSection
import net.kustax.opendelivery.web.ui.StatusChip
import net.kustax.opendelivery.web.util.formatEuros
import net.kustax.opendelivery.web.util.formatTimestamp
import net.kustax.opendelivery.web.util.ownerActions
import net.kustax.opendelivery.web.util.toLabel
import net.kustax.opendelivery.web.util.typeLabel

private val ALL_STATUSES = listOf("All", "PENDING", "CONFIRMED", "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED")

private fun validNextStatuses(current: String): List<String> = when (current) {
    "PENDING" -> listOf("CANCELLED")
    "CONFIRMED" -> listOf("PREPARING", "CANCELLED")
    "PREPARING" -> listOf("OUT_FOR_DELIVERY", "CANCELLED")
    "OUT_FOR_DELIVERY" -> listOf("DELIVERED", "CANCELLED")
    else -> emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    client: ApiClient,
    propertyId: String,
    propertyName: String,
    onBack: () -> Unit = {}
) {
    var orders by remember { mutableStateOf<List<OrderResponse>?>(null) }
    var selectedStatus by remember { mutableStateOf("All") }
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var orderForUpdate by remember { mutableStateOf<OrderResponse?>(null) }
    // Keyed by orderId; absent means not fetched yet, empty means no payment attempt recorded
    var paymentByOrderId by remember { mutableStateOf<Map<String, List<PaymentResponse>>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            orders = client.listOrders(
                propertyId = propertyId,
                status = selectedStatus.takeIf { it != "All" }
            )
            errorMessage = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    // One loop for the filter and the polling: changing the filter restarts it, so loads never overlap
    LaunchedEffect(selectedStatus) {
        while (true) {
            reload()
            delay(15_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbBar(listOf(
            "Properties" to onBack,
            propertyName to null
        ))

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Orders", style = MaterialTheme.typography.headlineSmall)
                Text(
                    propertyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExposedDropdownMenuBox(
                expanded = statusMenuExpanded,
                onExpandedChange = { statusMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (selectedStatus == "All") "All" else selectedStatus.toLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusMenuExpanded) },
                    modifier = Modifier.width(200.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false }
                ) {
                    ALL_STATUSES.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(if (s == "All") "All" else s.toLabel()) },
                            onClick = { selectedStatus = s; statusMenuExpanded = false }
                        )
                    }
                }
            }
        }

        errorMessage?.let {
            ErrorBanner(message = it, onDismiss = { errorMessage = null }, modifier = Modifier.padding(bottom = 8.dp))
        }

        when {
            orders == null -> LoadingState()
            orders!!.isEmpty() -> EmptyState("No orders found")
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(orders!!) { order ->
                        var expanded by remember { mutableStateOf(false) }

                        LaunchedEffect(expanded) {
                            if (expanded && !paymentByOrderId.containsKey(order.id)) {
                                val payments = try {
                                    client.getPayments(order.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                paymentByOrderId = paymentByOrderId + (order.id to payments)
                            }
                        }

                        OrderCard(
                            order = order,
                            expanded = expanded,
                            payments = paymentByOrderId[order.id],
                            onToggleExpand = { expanded = !expanded },
                            onUpdateClick = { orderForUpdate = order }
                        )
                    }
                }
            }
        }
    }

    orderForUpdate?.let { order ->
        val nextStatuses = order.ownerActions(validNextStatuses(order.status))
        AlertDialog(
            onDismissRequest = { orderForUpdate = null },
            title = { Text("Update Order Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusChip(order.status)
                    if (order.type == "FOOD_DELIVERY") {
                        Text(
                            "The delivery platform updates this order automatically; you can only cancel it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    nextStatuses.forEach { next ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        client.updateOrderStatus(order.id, next)
                                        orderForUpdate = null
                                        reload()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Set to ${next.toLabel()}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { orderForUpdate = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun OrderCard(
    order: OrderResponse,
    expanded: Boolean,
    payments: List<PaymentResponse>?,
    onToggleExpand: () -> Unit,
    onUpdateClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "#${order.id.takeLast(8).uppercase()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        StatusChip(order.status)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(order.typeLabel(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        order.guestEmail?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatTimestamp(order.createdAt), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        formatEuros(order.totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (order.ownerActions(validNextStatuses(order.status)).isNotEmpty()) {
                        TextButton(
                            onClick = onUpdateClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) { Text("Update", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }

            if (expanded) {
                if (order.items.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.productName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${item.quantity} × ${formatEuros(item.unitPrice)} = ${formatEuros(item.subtotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (order.deliveryFee > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delivery fee", style = MaterialTheme.typography.bodySmall)
                        Text(
                            formatEuros(order.deliveryFee),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                PaymentSection(payments)
            }
        }
    }
}
