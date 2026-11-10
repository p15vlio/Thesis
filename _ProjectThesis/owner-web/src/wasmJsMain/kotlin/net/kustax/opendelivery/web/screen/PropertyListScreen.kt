package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.request.CreatePropertyRequest
import net.kustax.opendelivery.data.request.UpdatePropertyRequest
import net.kustax.opendelivery.data.response.PropertyResponse
import net.kustax.opendelivery.web.util.rethrowCancellation
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.ActiveBadge
import net.kustax.opendelivery.web.ui.AddressSearch
import net.kustax.opendelivery.web.ui.ConfirmDialog
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.FoodPlatformBadges
import net.kustax.opendelivery.web.ui.FoodPlatformPicker
import net.kustax.opendelivery.web.ui.FormDialog
import net.kustax.opendelivery.web.ui.GuestInfoFields
import net.kustax.opendelivery.web.ui.isValidCheckOutTime
import net.kustax.opendelivery.web.ui.SnackbarController


@Composable
fun PropertyListScreen(
    client: ApiClient,
    onNavigateToDevices: (propertyId: String, propertyName: String) -> Unit,
    onNavigateToOrders: (propertyId: String, propertyName: String) -> Unit
) {
    var properties by remember { mutableStateOf(emptyList<PropertyResponse>()) }
    // Platforms the owner has connected, and which of them each property offers (keyed by property id)
    var connectedPlatforms by remember { mutableStateOf(emptyList<String>()) }
    var platformsByProperty by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProperty by remember { mutableStateOf<PropertyResponse?>(null) }
    var propertyToDelete by remember { mutableStateOf<PropertyResponse?>(null) }
    var propertyToDeactivate by remember { mutableStateOf<PropertyResponse?>(null) }
    var propertyToActivate by remember { mutableStateOf<PropertyResponse?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            properties = client.listProperties()
            connectedPlatforms = client.listFoodPlatforms().filter { it.connected }.map { it.platform }
            platformsByProperty = properties.associate { it.id to client.getPropertyFoodPlatforms(it.id).platforms }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    LaunchedEffect(Unit) { reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Text(
                "Properties",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            errorMessage?.let {
                ErrorBanner(message = it, onDismiss = { errorMessage = null }, modifier = Modifier.padding(bottom = 8.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(properties) { property ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(property.name, style = MaterialTheme.typography.titleMedium)
                                        ActiveBadge(property.isActive)
                                    }
                                    Text(
                                        "${property.streetName} ${property.streetNo}, ${property.postalCode} ${property.area ?: property.city}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FoodPlatformBadges(platformsByProperty[property.id].orEmpty())
                                }
                            }
                            HorizontalDivider()
                            // Action buttons row at the bottom
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onNavigateToDevices(property.id, property.name) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) { Text("Devices", style = MaterialTheme.typography.labelMedium) }
                                OutlinedButton(
                                    onClick = { onNavigateToOrders(property.id, property.name) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) { Text("Orders", style = MaterialTheme.typography.labelMedium) }
                                OutlinedButton(
                                    onClick = { editingProperty = property },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) { Text("Edit", style = MaterialTheme.typography.labelMedium) }
                                if (property.isActive) {
                                    OutlinedButton(
                                        onClick = { propertyToDeactivate = property },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) { Text("Deactivate", style = MaterialTheme.typography.labelMedium) }
                                } else {
                                    OutlinedButton(
                                        onClick = { propertyToActivate = property },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) { Text("Activate", style = MaterialTheme.typography.labelMedium) }
                                }
                                OutlinedButton(
                                    onClick = { propertyToDelete = property },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                ) { Text("Delete", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Property")
        }
    }

    if (showCreateDialog) {
        PropertyFormDialog(
            client = client,
            title = "Create Property",
            availablePlatforms = connectedPlatforms,
            onConfirm = { request, platforms ->
                scope.launch {
                    try {
                        val created = client.createProperty(request)
                        if (platforms.isNotEmpty()) client.setPropertyFoodPlatforms(created.id, platforms.toList())
                        showCreateDialog = false
                        reload()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                }
            },
            onDismiss = { showCreateDialog = false },
            confirmLabel = "Create"
        )
    }

    editingProperty?.let { existing ->
        PropertyFormDialog(
            client = client,
            title = "Edit Property",
            initialName = existing.name,
            initialStreetName = existing.streetName,
            initialStreetNo = existing.streetNo,
            initialPostalCode = existing.postalCode,
            initialArea = existing.area ?: "",
            initialLevel = existing.level,
            initialNameOnDoorbell = existing.nameOnDoorbell,
            initialContactPhone = existing.contactPhone,
            initialCity = existing.city,
            initialNotes = existing.notes ?: "",
            initialLatitude = existing.latitude,
            initialLongitude = existing.longitude,
            initialWifiName = existing.wifiName ?: "",
            initialWifiPassword = existing.wifiPassword ?: "",
            initialCheckOutTime = existing.checkOutTime ?: "",
            initialHouseRules = existing.houseRules ?: "",
            availablePlatforms = connectedPlatforms,
            initialPlatforms = platformsByProperty[existing.id].orEmpty(),
            onConfirm = { req, platforms ->
                scope.launch {
                    try {
                        client.updateProperty(
                            existing.id,
                            UpdatePropertyRequest(
                                name = req.name,
                                streetName = req.streetName,
                                streetNo = req.streetNo,
                                postalCode = req.postalCode,
                                area = req.area,
                                level = req.level,
                                nameOnDoorbell = req.nameOnDoorbell,
                                contactPhone = req.contactPhone,
                                city = req.city,
                                notes = req.notes,
                                latitude = req.latitude,
                                longitude = req.longitude,
                                wifiName = req.wifiName,
                                wifiPassword = req.wifiPassword,
                                checkOutTime = req.checkOutTime,
                                houseRules = req.houseRules
                            )
                        )
                        if (platforms != platformsByProperty[existing.id].orEmpty().toSet()) {
                            client.setPropertyFoodPlatforms(existing.id, platforms.toList())
                        }
                        editingProperty = null
                        reload()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                }
            },
            onDismiss = { editingProperty = null },
            confirmLabel = "Save"
        )
    }

    propertyToDelete?.let { property ->
        ConfirmDialog(
            title = "Delete Property",
            message = "Delete \"${property.name}\"? This action is permanent and cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    runCatching { client.deleteProperty(property.id) }.rethrowCancellation()
                        .onSuccess {
                            SnackbarController.show("Property deleted")
                            reload()
                        }
                        .onFailure { errorMessage = it.message }
                    propertyToDelete = null
                }
            },
            onDismiss = { propertyToDelete = null }
        )
    }

    propertyToDeactivate?.let { property ->
        ConfirmDialog(
            title = "Deactivate Property",
            message = "Deactivate \"${property.name}\"? This will also deactivate all devices registered under this property.",
            confirmLabel = "Deactivate",
            onConfirm = {
                scope.launch {
                    runCatching { client.deactivateProperty(property.id) }.rethrowCancellation()
                        .onSuccess {
                            SnackbarController.show("Property deactivated")
                            reload()
                        }
                        .onFailure { errorMessage = it.message }
                    propertyToDeactivate = null
                }
            },
            onDismiss = { propertyToDeactivate = null }
        )
    }

    propertyToActivate?.let { property ->
        ConfirmDialog(
            title = "Activate Property",
            message = "Activate \"${property.name}\"?",
            confirmLabel = "Activate",
            onConfirm = {
                scope.launch {
                    runCatching { client.activateProperty(property.id) }.rethrowCancellation()
                        .onSuccess {
                            SnackbarController.show("Property activated")
                            reload()
                        }
                        .onFailure { errorMessage = it.message }
                    propertyToActivate = null
                }
            },
            onDismiss = { propertyToActivate = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyFormDialog(
    client: ApiClient,
    title: String,
    initialName: String = "",
    initialStreetName: String = "",
    initialStreetNo: String = "",
    initialPostalCode: String = "",
    initialArea: String = "",
    initialLevel: Int = 0,
    initialNameOnDoorbell: String = "",
    initialContactPhone: String = "",
    initialCity: String = "",
    initialNotes: String = "",
    initialLatitude: Double? = null,
    initialLongitude: Double? = null,
    initialWifiName: String = "",
    initialWifiPassword: String = "",
    initialCheckOutTime: String = "",
    initialHouseRules: String = "",
    availablePlatforms: List<String>,
    initialPlatforms: List<String> = emptyList(),
    onConfirm: (CreatePropertyRequest, Set<String>) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Confirm"
) {
    var name by remember { mutableStateOf(initialName) }
    var streetName by remember { mutableStateOf(initialStreetName) }
    var streetNo by remember { mutableStateOf(initialStreetNo) }
    var postalCode by remember { mutableStateOf(initialPostalCode) }
    var area by remember { mutableStateOf(initialArea) }
    var levelText by remember { mutableStateOf(if (initialLevel == 0) "" else initialLevel.toString()) }
    var nameOnDoorbell by remember { mutableStateOf(initialNameOnDoorbell) }
    var contactPhone by remember { mutableStateOf(initialContactPhone) }
    var city by remember { mutableStateOf(initialCity) }
    var notes by remember { mutableStateOf(initialNotes) }
    var wifiName by remember { mutableStateOf(initialWifiName) }
    var wifiPassword by remember { mutableStateOf(initialWifiPassword) }
    var checkOutTime by remember { mutableStateOf(initialCheckOutTime) }
    var houseRules by remember { mutableStateOf(initialHouseRules) }
    var latitude by remember { mutableStateOf(initialLatitude) }
    var longitude by remember { mutableStateOf(initialLongitude) }
    var platforms by remember { mutableStateOf(initialPlatforms.toSet()) }

    FormDialog(
        title = title,
        onConfirm = {
            onConfirm(
                CreatePropertyRequest(
                    name = name,
                    streetName = streetName,
                    streetNo = streetNo,
                    postalCode = postalCode,
                    area = area.takeIf { it.isNotBlank() },
                    level = levelText.toIntOrNull() ?: 0,
                    nameOnDoorbell = nameOnDoorbell,
                    contactPhone = contactPhone,
                    city = city,
                    notes = notes.takeIf { it.isNotBlank() },
                    latitude = latitude,
                    longitude = longitude,
                    wifiName = wifiName.takeIf { it.isNotBlank() },
                    wifiPassword = wifiPassword.takeIf { it.isNotBlank() },
                    checkOutTime = checkOutTime.trim().takeIf { it.isNotBlank() },
                    houseRules = houseRules.takeIf { it.isNotBlank() }
                ),
                platforms
            )
        },
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank() && streetName.isNotBlank() && streetNo.isNotBlank()
            && postalCode.isNotBlank() && nameOnDoorbell.isNotBlank() && contactPhone.isNotBlank() && city.isNotBlank()
            && isValidCheckOutTime(checkOutTime),
        confirmLabel = confirmLabel
    ) {
        // Row 1: Property Name — full width
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Property Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        AddressSearch(
            client = client,
            latitude = latitude,
            longitude = longitude,
            onPick = { picked ->
                streetName = picked.streetName ?: streetName
                streetNo = picked.streetNo ?: streetNo
                postalCode = picked.postalCode ?: postalCode
                area = picked.area ?: area
                city = picked.city ?: city
                latitude = picked.latitude
                longitude = picked.longitude
            }
        )

        // Row 2: Street (weight 2) + No. (weight 1)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = streetName,
                onValueChange = { streetName = it },
                label = { Text("Street") },
                modifier = Modifier.weight(2f),
                singleLine = true
            )
            OutlinedTextField(
                value = streetNo,
                onValueChange = { streetNo = it },
                label = { Text("No.") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        // Row 3: Postal Code + Area
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = postalCode,
                onValueChange = { postalCode = it },
                label = { Text("Postal Code") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = area,
                onValueChange = { area = it },
                label = { Text("Area (optional)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        // Row 4: Floor (weight 1) + Name on Doorbell (weight 2)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = levelText,
                onValueChange = { levelText = it },
                label = { Text("Floor") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = nameOnDoorbell,
                onValueChange = { nameOnDoorbell = it },
                label = { Text("Name on Doorbell") },
                modifier = Modifier.weight(2f),
                singleLine = true
            )
        }

        // Row 5: Contact Phone + City
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("Contact Phone") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        FoodPlatformPicker(available = availablePlatforms, selected = platforms, onChange = { platforms = it })

        // Section divider
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                "NOTES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        // Row 7: Notes — full width, multi-line
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        GuestInfoFields(
            wifiName = wifiName,
            onWifiNameChange = { wifiName = it },
            wifiPassword = wifiPassword,
            onWifiPasswordChange = { wifiPassword = it },
            checkOutTime = checkOutTime,
            onCheckOutTimeChange = { checkOutTime = it },
            houseRules = houseRules,
            onHouseRulesChange = { houseRules = it }
        )
    }
}
