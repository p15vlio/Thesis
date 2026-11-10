package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.request.CreatePropertyRequest
import net.kustax.opendelivery.data.request.UpdatePropertyRequest
import net.kustax.opendelivery.data.response.OwnerWithStatsResponse
import net.kustax.opendelivery.data.response.PropertyResponse
import net.kustax.opendelivery.web.Screen
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.ActiveBadge
import net.kustax.opendelivery.web.ui.AddressSearch
import net.kustax.opendelivery.web.ui.ConfirmDialog
import net.kustax.opendelivery.web.ui.EmptyState
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.FoodPlatformBadges
import net.kustax.opendelivery.web.ui.FoodPlatformPicker
import net.kustax.opendelivery.web.ui.FormDialog
import net.kustax.opendelivery.web.ui.GuestInfoFields
import net.kustax.opendelivery.web.ui.isValidCheckOutTime
import net.kustax.opendelivery.web.ui.LoadingState
import net.kustax.opendelivery.web.ui.SnackbarController


@Composable
fun AdminPropertiesScreen(client: ApiClient, onNavigate: (Screen) -> Unit) {
    var ownerStats by remember { mutableStateOf<List<OwnerWithStatsResponse>>(emptyList()) }
    var propertiesByOwner by remember { mutableStateOf<Map<String, List<PropertyResponse>>>(emptyMap()) }
    // Connected platforms per owner (keyed by schemaName) and the ones each property offers (keyed by property id)
    var connectedBySchema by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var platformsByProperty by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var expandedOwners by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var addingPropertyForOwner by remember { mutableStateOf<OwnerWithStatsResponse?>(null) }
    var editingProperty by remember { mutableStateOf<Pair<PropertyResponse, String>?>(null) } // property + schemaName
    var confirmDeleteProperty by remember { mutableStateOf<Pair<PropertyResponse, String>?>(null) } // property + schemaName
    var confirmDeactivateProperty by remember { mutableStateOf<Pair<PropertyResponse, String>?>(null) } // property + schemaName
    var confirmActivateProperty by remember { mutableStateOf<Pair<PropertyResponse, String>?>(null) } // property + schemaName

    val scope = rememberCoroutineScope()

    suspend fun loadPlatforms(schemaName: String, properties: List<PropertyResponse>) {
        val connected = client.listFoodPlatforms(schemaName).filter { it.connected }.map { it.platform }
        val offered = properties.associate { it.id to client.getPropertyFoodPlatforms(it.id, schemaName).platforms }
        connectedBySchema = connectedBySchema + (schemaName to connected)
        platformsByProperty = platformsByProperty + offered
    }

    suspend fun loadAll() {
        try {
            val stats = client.getOwnerStats()
            ownerStats = stats
            expandedOwners = stats.map { it.owner.id }.toSet()
            val propMap = mutableMapOf<String, List<PropertyResponse>>()
            for (os in stats) {
                val ownerProperties = client.listPropertiesAdmin(os.owner.schemaName)
                propMap[os.owner.id] = ownerProperties
                loadPlatforms(os.owner.schemaName, ownerProperties)
            }
            propertiesByOwner = propMap
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    suspend fun reloadPropertiesForOwner(ownerId: String, schemaName: String) {
        try {
            val updated = client.listPropertiesAdmin(schemaName)
            propertiesByOwner = propertiesByOwner.toMutableMap().also { it[ownerId] = updated }
            loadPlatforms(schemaName, updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Properties", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search properties...") },
                singleLine = true,
                modifier = Modifier.width(240.dp)
            )
        }

        error?.let {
            ErrorBanner(message = it, onDismiss = { error = null }, modifier = Modifier.padding(bottom = 8.dp))
        }

        when {
            loading -> LoadingState()
            ownerStats.isEmpty() -> EmptyState("No owners found")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ownerStats.forEach { os ->
                        val ownerId = os.owner.id
                        val schemaName = os.owner.schemaName
                        val ownerProps = propertiesByOwner[ownerId] ?: emptyList()
                        val filteredProps = if (search.isBlank()) ownerProps
                            else ownerProps.filter {
                                it.name.contains(search, ignoreCase = true) ||
                                "${it.streetName} ${it.streetNo}".contains(search, ignoreCase = true)
                            }
                        val isExpanded = ownerId in expandedOwners

                        item(key = "header_$ownerId") {
                            OwnerSectionHeader(
                                displayName = os.owner.displayName,
                                propertyCount = ownerProps.size,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedOwners = if (isExpanded)
                                        expandedOwners - ownerId
                                    else
                                        expandedOwners + ownerId
                                },
                                onAddProperty = { addingPropertyForOwner = os }
                            )
                        }

                        if (isExpanded) {
                            if (filteredProps.isEmpty()) {
                                item(key = "empty_$ownerId") {
                                    EmptyState(
                                        if (search.isBlank()) "No properties for this owner"
                                        else "No matching properties",
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            } else {
                                items(filteredProps, key = { "prop_${it.id}" }) { property ->
                                    AdminPropertyCard(
                                        property = property,
                                        platforms = platformsByProperty[property.id].orEmpty(),
                                        onEdit = { editingProperty = property to schemaName },
                                        onDeactivate = { confirmDeactivateProperty = property to schemaName },
                                        onActivate = { confirmActivateProperty = property to schemaName },
                                        onDelete = { confirmDeleteProperty = property to schemaName },
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    addingPropertyForOwner?.let { os ->
        AdminPropertyFormDialog(
            client = client,
            title = "Create Property for ${os.owner.displayName}",
            confirmLabel = "Create",
            availablePlatforms = connectedBySchema[os.owner.schemaName].orEmpty(),
            onConfirm = { request, platforms ->
                scope.launch {
                    try {
                        val created = client.createPropertyAdmin(os.owner.schemaName, request)
                        if (platforms.isNotEmpty()) client.setPropertyFoodPlatforms(created.id, platforms.toList(), os.owner.schemaName)
                        addingPropertyForOwner = null
                        reloadPropertiesForOwner(os.owner.id, os.owner.schemaName)
                        SnackbarController.show("Property created")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { addingPropertyForOwner = null }
        )
    }

    editingProperty?.let { (property, schemaName) ->
        val ownerStat = ownerStats.find { it.owner.schemaName == schemaName }
        AdminPropertyFormDialog(
            client = client,
            title = "Edit Property",
            confirmLabel = "Save",
            initialName = property.name,
            initialStreetName = property.streetName,
            initialStreetNo = property.streetNo,
            initialPostalCode = property.postalCode,
            initialArea = property.area ?: "",
            initialLevel = property.level,
            initialNameOnDoorbell = property.nameOnDoorbell,
            initialContactPhone = property.contactPhone,
            initialCity = property.city,
            initialNotes = property.notes ?: "",
            initialLatitude = property.latitude,
            initialLongitude = property.longitude,
            initialWifiName = property.wifiName ?: "",
            initialWifiPassword = property.wifiPassword ?: "",
            initialCheckOutTime = property.checkOutTime ?: "",
            initialHouseRules = property.houseRules ?: "",
            availablePlatforms = connectedBySchema[schemaName].orEmpty(),
            initialPlatforms = platformsByProperty[property.id].orEmpty(),
            onConfirm = { request, platforms ->
                scope.launch {
                    try {
                        client.updatePropertyAdmin(
                            property.id,
                            schemaName,
                            UpdatePropertyRequest(
                                name = request.name,
                                streetName = request.streetName,
                                streetNo = request.streetNo,
                                postalCode = request.postalCode,
                                area = request.area,
                                level = request.level,
                                nameOnDoorbell = request.nameOnDoorbell,
                                contactPhone = request.contactPhone,
                                city = request.city,
                                notes = request.notes,
                                latitude = request.latitude,
                                longitude = request.longitude,
                                wifiName = request.wifiName,
                                wifiPassword = request.wifiPassword,
                                checkOutTime = request.checkOutTime,
                                houseRules = request.houseRules
                            )
                        )
                        if (platforms != platformsByProperty[property.id].orEmpty().toSet()) {
                            client.setPropertyFoodPlatforms(property.id, platforms.toList(), schemaName)
                        }
                        editingProperty = null
                        ownerStat?.let { reloadPropertiesForOwner(it.owner.id, schemaName) }
                        SnackbarController.show("Property updated")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { editingProperty = null }
        )
    }

    confirmDeleteProperty?.let { (property, schemaName) ->
        val ownerStat = ownerStats.find { it.owner.schemaName == schemaName }
        ConfirmDialog(
            title = "Delete Property",
            message = "Delete \"${property.name}\"? This action is permanent and cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    try {
                        client.deleteProperty(property.id)
                        ownerStat?.let { reloadPropertiesForOwner(it.owner.id, schemaName) }
                        SnackbarController.show("Property deleted")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        confirmDeleteProperty = null
                    }
                }
            },
            onDismiss = { confirmDeleteProperty = null }
        )
    }

    confirmDeactivateProperty?.let { (property, schemaName) ->
        val ownerStat = ownerStats.find { it.owner.schemaName == schemaName }
        ConfirmDialog(
            title = "Deactivate Property",
            message = "Deactivate \"${property.name}\"? This will also deactivate all devices registered under this property.",
            confirmLabel = "Deactivate",
            onConfirm = {
                scope.launch {
                    try {
                        client.deactivatePropertyAdmin(property.id, schemaName)
                        ownerStat?.let { reloadPropertiesForOwner(it.owner.id, schemaName) }
                        SnackbarController.show("Property deactivated")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        confirmDeactivateProperty = null
                    }
                }
            },
            onDismiss = { confirmDeactivateProperty = null }
        )
    }

    confirmActivateProperty?.let { (property, schemaName) ->
        val ownerStat = ownerStats.find { it.owner.schemaName == schemaName }
        ConfirmDialog(
            title = "Activate Property",
            message = "Activate \"${property.name}\"?",
            confirmLabel = "Activate",
            onConfirm = {
                scope.launch {
                    try {
                        client.activatePropertyAdmin(property.id, schemaName)
                        ownerStat?.let { reloadPropertiesForOwner(it.owner.id, schemaName) }
                        SnackbarController.show("Property activated")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        confirmActivateProperty = null
                    }
                }
            },
            onDismiss = { confirmActivateProperty = null }
        )
    }
}

@Composable
private fun OwnerSectionHeader(
    displayName: String,
    propertyCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAddProperty: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "($propertyCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onAddProperty,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Property", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AdminPropertyCard(
    property: PropertyResponse,
    platforms: List<String>,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(property.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${property.streetName} ${property.streetNo}, ${property.postalCode} ${property.area ?: property.city}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActiveBadge(property.isActive)
                FoodPlatformBadges(platforms)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (property.isActive) {
                    IconButton(onClick = onDeactivate) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = "Deactivate",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onActivate) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Activate",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPropertyFormDialog(
    client: ApiClient,
    title: String,
    confirmLabel: String,
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
    onDismiss: () -> Unit
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
        confirmLabel = confirmLabel,
        confirmEnabled = name.isNotBlank() && streetName.isNotBlank() && streetNo.isNotBlank()
            && postalCode.isNotBlank() && nameOnDoorbell.isNotBlank() && contactPhone.isNotBlank() && city.isNotBlank()
            && isValidCheckOutTime(checkOutTime),
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
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Property Name") },
            modifier = Modifier.fillMaxWidth()
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

        OutlinedTextField(
            value = streetName,
            onValueChange = { streetName = it },
            label = { Text("Street Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = streetNo,
                onValueChange = { streetNo = it },
                label = { Text("Street No.") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = postalCode,
                onValueChange = { postalCode = it },
                label = { Text("Postal Code") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = area,
                onValueChange = { area = it },
                label = { Text("Area (optional)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = levelText,
                onValueChange = { levelText = it },
                label = { Text("Floor") },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = nameOnDoorbell,
            onValueChange = { nameOnDoorbell = it },
            label = { Text("Name on Doorbell") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("Contact Phone") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                modifier = Modifier.weight(1f)
            )
        }
        FoodPlatformPicker(available = availablePlatforms, selected = platforms, onChange = { platforms = it })
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth()
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
