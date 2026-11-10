package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.request.UpdateGemhSettingsRequest
import net.kustax.opendelivery.data.request.UpdateSmtpSettingsRequest
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.SnackbarController

private val SMTP_SECURITY_OPTIONS = listOf("STARTTLS", "SSL", "NONE")

@Composable
fun SettingsScreen(client: ApiClient) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SmtpSettingsCard(client)

        GemhSettingsCard(client)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Session", style = MaterialTheme.typography.headlineSmall)
                Text("Access token TTL: 15 minutes (owner/platform admin sessions)", style = MaterialTheme.typography.bodyMedium)
                Text("Kiosk device token TTL: 1 year", style = MaterialTheme.typography.bodyMedium)
                Text("Refresh token TTL: 30 days", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Session settings are configured in backend environment variables.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmtpSettingsCard(client: ApiClient) {
    var loaded by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("STARTTLS") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordSet by remember { mutableStateOf(false) }
    var fromAddress by remember { mutableStateOf("") }
    var fromName by remember { mutableStateOf("") }
    var testRecipient by remember { mutableStateOf("") }
    var securityExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val s = client.getSmtpSettings()
            host = s.host
            port = s.port.toString()
            security = s.security
            username = s.username
            passwordSet = s.passwordSet
            fromAddress = s.fromAddress
            fromName = s.fromName
            loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Email (SMTP)", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Used for owner invitations and order receipts. For Gmail use smtp.gmail.com, port 587, STARTTLS, " +
                    "your Gmail address as username and sender, and a Google app password (not your account password).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            errorMessage?.let { ErrorBanner(message = it, onDismiss = { errorMessage = null }) }

            if (!loaded) return@Column

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("SMTP host") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { v -> port = v.filter { it.isDigit() }.take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                ExposedDropdownMenuBox(
                    expanded = securityExpanded,
                    onExpandedChange = { securityExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = security,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Security") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = securityExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = securityExpanded, onDismissRequest = { securityExpanded = false }) {
                        SMTP_SECURITY_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    security = option
                                    securityExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (passwordSet) "App password (saved — leave blank to keep)" else "App password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fromAddress,
                    onValueChange = { fromAddress = it },
                    label = { Text("Sender address") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fromName,
                    onValueChange = { fromName = it },
                    label = { Text("Sender name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                enabled = !saving,
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            val saved = client.updateSmtpSettings(
                                UpdateSmtpSettingsRequest(
                                    host = host,
                                    port = port.toIntOrNull() ?: 0,
                                    security = security,
                                    username = username,
                                    password = password.ifBlank { null },
                                    fromAddress = fromAddress,
                                    fromName = fromName
                                )
                            )
                            passwordSet = saved.passwordSet
                            password = ""
                            errorMessage = null
                            SnackbarController.show("SMTP settings saved")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = e.message
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(if (saving) "Saving…" else "Save") }

            HorizontalDivider()

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = testRecipient,
                    onValueChange = { testRecipient = it },
                    label = { Text("Send test email to") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    enabled = !testing && testRecipient.isNotBlank(),
                    onClick = {
                        scope.launch {
                            testing = true
                            try {
                                client.sendTestEmail(testRecipient.trim())
                                errorMessage = null
                                SnackbarController.show("Test email sent to ${testRecipient.trim()}")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                testing = false
                            }
                        }
                    }
                ) { Text(if (testing) "Sending…" else "Send test") }
            }
            Text(
                "The test uses the saved settings — save first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GemhSettingsCard(client: ApiClient) {
    var loaded by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeySet by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            apiKeySet = client.getGemhSettings().apiKeySet
            loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("GEMH (Business Registry)", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Used to look up a Greek company by VAT ID when creating an owner. Request a personal API key at " +
                    "opendata.businessportal.gr.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            errorMessage?.let { ErrorBanner(message = it, onDismiss = { errorMessage = null }) }

            if (!loaded) return@Column

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (apiKeySet) "API key (saved — leave blank to keep)" else "API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = !saving && apiKey.isNotBlank(),
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            apiKeySet = client.updateGemhSettings(UpdateGemhSettingsRequest(apiKey.trim())).apiKeySet
                            apiKey = ""
                            errorMessage = null
                            SnackbarController.show("GEMH settings saved")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = e.message
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        }
    }
}
