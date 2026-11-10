package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.request.ImportProductsRequest
import net.kustax.opendelivery.data.response.CatalogProductResponse
import net.kustax.opendelivery.data.response.ImportProductsResponse
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.util.formatEuros
import net.kustax.opendelivery.web.util.toLabel
import net.kustax.opendelivery.web.util.toPlatformLabel

private val IMPORT_CATEGORIES = listOf(
    "BEVERAGE", "HOT_DRINK", "FOOD", "SNACK", "ALCOHOL", "PERSONAL_CARE", "PHARMACY", "OTHER"
)

// Imports in-room store products from the retail catalog of a connected food platform. schemaName is only set
// when a PLATFORM_ADMIN acts on an owner's store.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportProductsDialog(
    client: ApiClient,
    connectedPlatforms: List<String>,
    schemaName: String? = null,
    onImported: (ImportProductsResponse) -> Unit,
    onDismiss: () -> Unit
) {
    var platform by remember { mutableStateOf(connectedPlatforms.first()) }
    var catalog by remember { mutableStateOf<List<CatalogProductResponse>?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(platform) {
        catalog = null
        selected = emptySet()
        try {
            catalog = client.listFoodPlatformCatalog(platform, schemaName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
            catalog = emptyList()
        }
    }

    val filtered = (catalog ?: emptyList()).filter { item ->
        (categoryFilter == null || item.category == categoryFilter) &&
            (search.isBlank() || item.name.contains(search.trim(), ignoreCase = true) ||
                item.brand?.contains(search.trim(), ignoreCase = true) == true)
    }
    val allFilteredSelected = filtered.isNotEmpty() && filtered.all { it.id in selected }

    FormDialog(
        title = "Import products from a platform",
        onConfirm = {
            importing = true
            scope.launch {
                try {
                    onImported(client.importProducts(ImportProductsRequest(platform, selected.toList()), schemaName))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorMessage = e.message
                    importing = false
                }
            }
        },
        onDismiss = onDismiss,
        confirmEnabled = selected.isNotEmpty() && !importing,
        confirmLabel = if (selected.isEmpty()) "Import" else "Import (${selected.size})"
    ) {
        errorMessage?.let { ErrorBanner(message = it, onDismiss = { errorMessage = null }) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Platform", style = MaterialTheme.typography.labelLarge)
            connectedPlatforms.forEach { p ->
                FilterChip(
                    selected = p == platform,
                    onClick = { platform = p },
                    label = { Text(p.toPlatformLabel()) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = categoryFilter?.toLabel() ?: "All categories",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All categories") },
                        onClick = { categoryFilter = null; categoryMenuExpanded = false }
                    )
                    IMPORT_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.toLabel()) },
                            onClick = { categoryFilter = cat; categoryMenuExpanded = false }
                        )
                    }
                }
            }
        }

        if (catalog == null) {
            LoadingState()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${filtered.size} products · ${selected.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    enabled = filtered.isNotEmpty(),
                    onClick = {
                        val ids = filtered.map { it.id }.toSet()
                        selected = if (allFilteredSelected) selected - ids else selected + ids
                    }
                ) { Text(if (allFilteredSelected) "Deselect all (filtered)" else "Select all (filtered)") }
            }

            if (filtered.isEmpty()) {
                EmptyState("No products match")
            }
            IMPORT_CATEGORIES.forEach { cat ->
                val items = filtered.filter { it.category == cat }
                if (items.isNotEmpty()) {
                    Text(
                        cat.toLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + item.id else selected - item.id
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                formatEuros(item.priceCents),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
