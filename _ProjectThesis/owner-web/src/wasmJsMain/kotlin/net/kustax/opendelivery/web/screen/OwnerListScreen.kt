@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kustax.opendelivery.web.Screen
import net.kustax.opendelivery.data.request.CreateOwnerRequest
import net.kustax.opendelivery.data.request.UpdateOwnerRequest
import net.kustax.opendelivery.data.response.OwnerResponse
import net.kustax.opendelivery.data.response.OwnerWithStatsResponse
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.ActiveBadge
import net.kustax.opendelivery.web.ui.ConfirmDialog
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.FormDialog
import net.kustax.opendelivery.web.ui.SnackbarController
import net.kustax.opendelivery.web.util.formatTimestamp

@Composable
fun OwnerListScreen(client: ApiClient, onNavigate: (Screen) -> Unit = {}) {
    var ownerStats by remember { mutableStateOf(emptyList<OwnerWithStatsResponse>()) }
    var search by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingOwner by remember { mutableStateOf<OwnerResponse?>(null) }
    var createdOwnerPassword by remember { mutableStateOf<String?>(null) }
    var ownerToDeactivate by remember { mutableStateOf<OwnerResponse?>(null) }
    var ownerToActivate by remember { mutableStateOf<OwnerResponse?>(null) }
    var ownerToReset by remember { mutableStateOf<OwnerResponse?>(null) }
    var resetPassword by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            ownerStats = client.getOwnerStats()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    LaunchedEffect(Unit) { reload() }

    val filtered = if (search.isBlank()) ownerStats
        else ownerStats.filter {
            it.owner.displayName.contains(search, ignoreCase = true) ||
            it.owner.contactEmail.contains(search, ignoreCase = true)
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Owners", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search owners...") },
                    singleLine = true,
                    modifier = Modifier.width(220.dp)
                )
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Owner")
                }
            }
        }

        errorMessage?.let {
            ErrorBanner(message = it, onDismiss = { errorMessage = null }, modifier = Modifier.padding(bottom = 8.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { item ->
                val owner = item.owner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(owner.displayName, style = MaterialTheme.typography.titleMedium)
                                ActiveBadge(owner.isActive)
                            }
                            Text(
                                owner.contactEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "VAT: ${owner.vatId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${listOfNotNull(owner.streetName, owner.streetNo).joinToString(" ")}, ${owner.postalCode} ${owner.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                StatBadge("${item.propertyCount}", "Properties")
                                StatBadge("${item.deviceCount}", "Devices")
                                StatBadge("${item.productCount}", "Products")
                            }
                            Text(
                                "Since ${formatTimestamp(owner.createdAt).take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        // Action icons — view, edit, delete
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            IconButton(onClick = {
                                onNavigate(Screen.AdminOwnerView(owner.id, owner.schemaName, owner.displayName))
                            }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "View", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { editingOwner = owner }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { ownerToReset = owner }) {
                                Icon(Icons.Default.LockReset, contentDescription = "Reset password", modifier = Modifier.size(18.dp))
                            }
                            if (owner.isActive) {
                                IconButton(onClick = { ownerToDeactivate = owner }) {
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = "Deactivate",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                IconButton(onClick = { ownerToActivate = owner }) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Activate",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOwnerDialog(
            client = client,
            onConfirm = { request ->
                scope.launch {
                    try {
                        val created = client.createOwner(request)
                        showCreateDialog = false
                        if (created.temporaryPassword != null) {
                            createdOwnerPassword = created.temporaryPassword
                        } else {
                            SnackbarController.show("Owner created")
                            reload()
                        }
                    }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { errorMessage = e.message }
                }
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    createdOwnerPassword?.let { tempPassword ->
        TempPasswordDialog(
            tempPassword = tempPassword,
            onDismiss = {
                createdOwnerPassword = null
                SnackbarController.show("Owner created")
                scope.launch { reload() }
            }
        )
    }

    editingOwner?.let { owner ->
        EditOwnerDialog(
            owner = owner,
            onConfirm = { request ->
                scope.launch {
                    try {
                        client.updateOwner(owner.id, request)
                        editingOwner = null
                        SnackbarController.show("Owner updated")
                        reload()
                    }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { errorMessage = e.message }
                }
            },
            onDismiss = { editingOwner = null }
        )
    }

    ownerToDeactivate?.let { owner ->
        ConfirmDialog(
            title = "Deactivate Owner",
            message = "Deactivate ${owner.displayName}? They will lose access until reactivated.",
            confirmLabel = "Deactivate",
            onConfirm = {
                ownerToDeactivate = null
                scope.launch {
                    try {
                        client.deactivateOwner(owner.id)
                        SnackbarController.show("Owner deactivated")
                        reload()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { errorMessage = e.message }
                }
            },
            onDismiss = { ownerToDeactivate = null }
        )
    }

    ownerToReset?.let { owner ->
        ConfirmDialog(
            title = "Reset Password",
            message = "Reset the password of ${owner.displayName}? A new temporary password is emailed to ${owner.contactEmail} and all their sessions are signed out.",
            confirmLabel = "Reset",
            onConfirm = {
                ownerToReset = null
                scope.launch {
                    try {
                        resetPassword = client.resetOwnerPassword(owner.id).temporaryPassword
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { errorMessage = e.message }
                }
            },
            onDismiss = { ownerToReset = null }
        )
    }

    resetPassword?.let { tempPassword ->
        TempPasswordDialog(
            tempPassword = tempPassword,
            title = "Password Reset",
            message = "The password has been reset and emailed to the owner. Share this temporary password with them if the email does not arrive — it will not be shown again.",
            onDismiss = { resetPassword = null }
        )
    }

    ownerToActivate?.let { owner ->
        ConfirmDialog(
            title = "Activate Owner",
            message = "Activate ${owner.displayName}?",
            confirmLabel = "Activate",
            onConfirm = {
                ownerToActivate = null
                scope.launch {
                    try {
                        client.activateOwner(owner.id)
                        SnackbarController.show("Owner activated")
                        reload()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { errorMessage = e.message }
                }
            },
            onDismiss = { ownerToActivate = null }
        )
    }
}

@Composable
private fun StatBadge(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionDivider(label: String) {
    Spacer(Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CreateOwnerDialog(client: ApiClient, onConfirm: (CreateOwnerRequest) -> Unit, onDismiss: () -> Unit) {
    var vatId by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var distinctiveTitle by remember { mutableStateOf("") }
    var gemhNumber by remember { mutableStateOf("") }
    var legalForm by remember { mutableStateOf("") }
    var companyActivity by remember { mutableStateOf("") }
    var streetName by remember { mutableStateOf("") }
    var streetNo by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var contactFirstName by remember { mutableStateOf("") }
    var contactLastName by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var secondaryContactName by remember { mutableStateOf("") }
    var secondaryContactPhone by remember { mutableStateOf("") }
    var secondaryContactEmail by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var gemhStatusWarning by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val vatIdValid = vatId.length == 9
    val requiredFieldsFilled = vatIdValid && companyName.isNotBlank() && streetName.isNotBlank() &&
        postalCode.isNotBlank() && city.isNotBlank() && contactFirstName.isNotBlank() &&
        contactLastName.isNotBlank() && contactEmail.isNotBlank() && contactPhone.isNotBlank()

    FormDialog(
        title = "Create Owner",
        onConfirm = {
            onConfirm(
                CreateOwnerRequest(
                    companyName = companyName,
                    distinctiveTitle = distinctiveTitle.takeIf { it.isNotBlank() },
                    vatId = vatId,
                    gemhNumber = gemhNumber.takeIf { it.isNotBlank() },
                    legalForm = legalForm.takeIf { it.isNotBlank() },
                    companyActivity = companyActivity.takeIf { it.isNotBlank() },
                    streetName = streetName,
                    streetNo = streetNo.takeIf { it.isNotBlank() },
                    postalCode = postalCode,
                    city = city,
                    contactFirstName = contactFirstName,
                    contactLastName = contactLastName,
                    contactEmail = contactEmail,
                    contactPhone = contactPhone,
                    secondaryContactName = secondaryContactName.takeIf { it.isNotBlank() },
                    secondaryContactPhone = secondaryContactPhone.takeIf { it.isNotBlank() },
                    secondaryContactEmail = secondaryContactEmail.takeIf { it.isNotBlank() },
                    website = website.takeIf { it.isNotBlank() },
                    notes = notes.takeIf { it.isNotBlank() }
                )
            )
        },
        onDismiss = onDismiss,
        confirmEnabled = requiredFieldsFilled,
        confirmLabel = "Create"
    ) {
        // ── COMPANY ───────────────────────────────────────────────
        SectionDivider("COMPANY")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = vatId,
                onValueChange = { v -> vatId = v.filter { it.isDigit() }.take(9) },
                label = { Text("VAT ID (AFM)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedButton(
                enabled = vatIdValid && !searching,
                onClick = {
                    scope.launch {
                        searching = true
                        try {
                            val found = client.lookupCompany(vatId)
                            companyName = found.companyName
                            distinctiveTitle = found.distinctiveTitle.orEmpty()
                            gemhNumber = found.gemhNumber.orEmpty()
                            legalForm = found.legalForm.orEmpty()
                            companyActivity = found.companyActivity.orEmpty()
                            streetName = found.streetName.orEmpty()
                            streetNo = found.streetNo.orEmpty()
                            postalCode = found.postalCode.orEmpty()
                            city = found.city.orEmpty()
                            website = found.website.orEmpty()
                            gemhStatusWarning = if (found.isActive) null
                                else "GEMH reports this company as not active${found.statusDescription?.let { ": $it" }.orEmpty()}."
                            lookupError = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lookupError = e.message
                            gemhStatusWarning = null
                        } finally {
                            searching = false
                        }
                    }
                }
            ) { Text(if (searching) "Searching…" else "Search GEMH") }
        }
        lookupError?.let { ErrorBanner(message = it, onDismiss = { lookupError = null }) }
        gemhStatusWarning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = companyName,
            onValueChange = { companyName = it },
            label = { Text("Company Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = distinctiveTitle,
            onValueChange = { distinctiveTitle = it },
            label = { Text("Distinctive Title (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = gemhNumber,
                onValueChange = { gemhNumber = it },
                label = { Text("GEMH Number") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = legalForm,
                onValueChange = { legalForm = it },
                label = { Text("Legal Form") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = companyActivity,
            onValueChange = { companyActivity = it },
            label = { Text("Company Activity") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // ── ADDRESS ───────────────────────────────────────────────
        SectionDivider("ADDRESS")

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = postalCode,
                onValueChange = { postalCode = it },
                label = { Text("Postal Code") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                modifier = Modifier.weight(2f),
                singleLine = true
            )
        }

        // ── CONTACT ───────────────────────────────────────────────
        SectionDivider("CONTACT")

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = contactFirstName,
                onValueChange = { contactFirstName = it },
                label = { Text("Contact First Name") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = contactLastName,
                onValueChange = { contactLastName = it },
                label = { Text("Contact Last Name") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = contactEmail,
                onValueChange = { contactEmail = it },
                label = { Text("Contact Email") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("Contact Phone") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        // ── OPTIONAL DETAILS ──────────────────────────────────────
        SectionDivider("OPTIONAL DETAILS")

        OutlinedTextField(
            value = website,
            onValueChange = { website = it },
            label = { Text("Website") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = secondaryContactName,
            onValueChange = { secondaryContactName = it },
            label = { Text("Secondary Contact Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = secondaryContactPhone,
                onValueChange = { secondaryContactPhone = it },
                label = { Text("Secondary Phone") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = secondaryContactEmail,
                onValueChange = { secondaryContactEmail = it },
                label = { Text("Secondary Email") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
    }
}

@Composable
private fun TempPasswordDialog(
    tempPassword: String,
    onDismiss: () -> Unit,
    title: String = "Owner Created",
    message: String = "The owner has been created. Share this temporary password with them — it will not be shown again."
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            tempPassword,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        IconButton(
                            onClick = { window.navigator.clipboard.writeText(tempPassword) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy password",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun EditOwnerDialog(
    owner: OwnerResponse,
    onConfirm: (UpdateOwnerRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var companyName by remember { mutableStateOf(owner.companyName) }
    var distinctiveTitle by remember { mutableStateOf(owner.distinctiveTitle ?: "") }
    var gemhNumber by remember { mutableStateOf(owner.gemhNumber ?: "") }
    var legalForm by remember { mutableStateOf(owner.legalForm ?: "") }
    var companyActivity by remember { mutableStateOf(owner.companyActivity ?: "") }
    var streetName by remember { mutableStateOf(owner.streetName) }
    var streetNo by remember { mutableStateOf(owner.streetNo ?: "") }
    var postalCode by remember { mutableStateOf(owner.postalCode) }
    var city by remember { mutableStateOf(owner.city) }
    var contactFirstName by remember { mutableStateOf(owner.contactFirstName) }
    var contactLastName by remember { mutableStateOf(owner.contactLastName) }
    var contactPhone by remember { mutableStateOf(owner.contactPhone) }
    var secondaryContactName by remember { mutableStateOf(owner.secondaryContactName ?: "") }
    var secondaryContactPhone by remember { mutableStateOf(owner.secondaryContactPhone ?: "") }
    var secondaryContactEmail by remember { mutableStateOf(owner.secondaryContactEmail ?: "") }
    var website by remember { mutableStateOf(owner.website ?: "") }
    var notes by remember { mutableStateOf(owner.notes ?: "") }

    FormDialog(
        title = "Edit Owner",
        onConfirm = {
            onConfirm(
                UpdateOwnerRequest(
                    companyName = companyName,
                    distinctiveTitle = distinctiveTitle.takeIf { it.isNotBlank() },
                    gemhNumber = gemhNumber.takeIf { it.isNotBlank() },
                    legalForm = legalForm.takeIf { it.isNotBlank() },
                    companyActivity = companyActivity.takeIf { it.isNotBlank() },
                    streetName = streetName,
                    streetNo = streetNo.takeIf { it.isNotBlank() },
                    postalCode = postalCode,
                    city = city,
                    contactFirstName = contactFirstName,
                    contactLastName = contactLastName,
                    contactPhone = contactPhone,
                    secondaryContactName = secondaryContactName.takeIf { it.isNotBlank() },
                    secondaryContactPhone = secondaryContactPhone.takeIf { it.isNotBlank() },
                    secondaryContactEmail = secondaryContactEmail.takeIf { it.isNotBlank() },
                    website = website.takeIf { it.isNotBlank() },
                    notes = notes.takeIf { it.isNotBlank() }
                )
            )
        },
        onDismiss = onDismiss,
        confirmEnabled = companyName.isNotBlank() && streetName.isNotBlank() && postalCode.isNotBlank() &&
            city.isNotBlank() && contactFirstName.isNotBlank() && contactLastName.isNotBlank() &&
            contactPhone.isNotBlank(),
        confirmLabel = "Save"
    ) {
        // ── COMPANY ───────────────────────────────────────────────
        SectionDivider("COMPANY")

        // Read-only identity fields shown side by side
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = owner.vatId,
                onValueChange = {},
                readOnly = true,
                label = { Text("VAT ID (AFM)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = owner.contactEmail,
                onValueChange = {},
                readOnly = true,
                label = { Text("Contact Email") },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = companyName,
            onValueChange = { companyName = it },
            label = { Text("Company Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = distinctiveTitle,
            onValueChange = { distinctiveTitle = it },
            label = { Text("Distinctive Title (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = gemhNumber,
                onValueChange = { gemhNumber = it },
                label = { Text("GEMH Number") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = legalForm,
                onValueChange = { legalForm = it },
                label = { Text("Legal Form") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = companyActivity,
            onValueChange = { companyActivity = it },
            label = { Text("Company Activity") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // ── ADDRESS ───────────────────────────────────────────────
        SectionDivider("ADDRESS")

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = postalCode,
                onValueChange = { postalCode = it },
                label = { Text("Postal Code") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                modifier = Modifier.weight(2f),
                singleLine = true
            )
        }

        // ── CONTACT ───────────────────────────────────────────────
        SectionDivider("CONTACT")

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = contactFirstName,
                onValueChange = { contactFirstName = it },
                label = { Text("Contact First Name") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = contactLastName,
                onValueChange = { contactLastName = it },
                label = { Text("Contact Last Name") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = contactPhone,
            onValueChange = { contactPhone = it },
            label = { Text("Contact Phone") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // ── OPTIONAL DETAILS ──────────────────────────────────────
        SectionDivider("OPTIONAL DETAILS")

        OutlinedTextField(
            value = website,
            onValueChange = { website = it },
            label = { Text("Website") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = secondaryContactName,
            onValueChange = { secondaryContactName = it },
            label = { Text("Secondary Contact Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = secondaryContactPhone,
                onValueChange = { secondaryContactPhone = it },
                label = { Text("Secondary Phone") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = secondaryContactEmail,
                onValueChange = { secondaryContactEmail = it },
                label = { Text("Secondary Email") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
    }
}
