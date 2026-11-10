package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.response.AuditLogResponse
import net.kustax.opendelivery.web.Screen
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.EmptyState
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.LoadingState
import net.kustax.opendelivery.web.util.formatTimestamp
import net.kustax.opendelivery.web.util.toLabel

private val ACTION_FILTERS = listOf("All", "CREATE", "UPDATE", "DEACTIVATE", "DELETE")
private val TARGET_TYPE_FILTERS = listOf("All", "OWNER", "PROPERTY", "DEVICE", "PRODUCT", "ORDER")
private const val PAGE_SIZE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(client: ApiClient, onNavigate: (Screen) -> Unit) {
    var entries by remember { mutableStateOf<List<AuditLogResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(0L) }
    var filterAction by remember { mutableStateOf<String?>(null) }
    var filterTargetType by remember { mutableStateOf<String?>(null) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var targetTypeMenuExpanded by remember { mutableStateOf(false) }
    var expandedEntryId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            entries = client.listAuditLogs(PAGE_SIZE, 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    val filtered = entries.filter { entry ->
        (filterAction == null || entry.action.startsWith(filterAction!!)) &&
        (filterTargetType == null || entry.targetType == filterTargetType)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Audit Log", style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = actionMenuExpanded,
                    onExpandedChange = { actionMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filterAction ?: "All",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionMenuExpanded) },
                        modifier = Modifier.width(180.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = actionMenuExpanded,
                        onDismissRequest = { actionMenuExpanded = false }
                    ) {
                        ACTION_FILTERS.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter) },
                                onClick = {
                                    filterAction = if (filter == "All") null else filter
                                    actionMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = targetTypeMenuExpanded,
                    onExpandedChange = { targetTypeMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filterTargetType ?: "All",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Entity Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetTypeMenuExpanded) },
                        modifier = Modifier.width(180.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = targetTypeMenuExpanded,
                        onDismissRequest = { targetTypeMenuExpanded = false }
                    ) {
                        TARGET_TYPE_FILTERS.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter) },
                                onClick = {
                                    filterTargetType = if (filter == "All") null else filter
                                    targetTypeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        error?.let {
            ErrorBanner(message = it, onDismiss = { error = null }, modifier = Modifier.padding(bottom = 8.dp))
        }

        when {
            loading -> LoadingState()
            filtered.isEmpty() -> EmptyState("No audit log entries")
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        AuditLogRow(
                            entry = entry,
                            expanded = expandedEntryId == entry.id,
                            onToggleExpand = {
                                expandedEntryId = if (expandedEntryId == entry.id) null else entry.id
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val next = client.listAuditLogs(PAGE_SIZE, offset + PAGE_SIZE)
                                entries = entries + next
                                offset += PAGE_SIZE
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Load More")
                }
            }
        }
    }
}

@Composable
private fun AuditLogRow(
    entry: AuditLogResponse,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val dotColor = when {
        entry.action.startsWith("CREATE")     -> Color(0xFF22C55E)
        entry.action.startsWith("UPDATE")     -> Color(0xFFF59E0B)
        entry.action.startsWith("DEACTIVATE") -> Color(0xFFF97316)
        entry.action.startsWith("DELETE")     -> MaterialTheme.colorScheme.error
        else                                  -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape)
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.actorRole.toLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            entry.action.toLabel(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.targetType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val shortId = entry.targetId?.takeLast(8) ?: "-"
                        Text(
                            shortId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatTimestamp(entry.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    entry.schemaName?.let { schema ->
                        Text(
                            "Tenant: $schema",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            if (expanded && entry.details != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        entry.details!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
