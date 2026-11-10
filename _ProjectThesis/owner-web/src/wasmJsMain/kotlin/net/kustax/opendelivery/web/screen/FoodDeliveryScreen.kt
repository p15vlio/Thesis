package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kustax.opendelivery.data.response.FoodPlatformResponse
import net.kustax.opendelivery.web.util.rethrowCancellation
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.ui.ConfirmDialog
import net.kustax.opendelivery.web.ui.ErrorBanner
import net.kustax.opendelivery.web.ui.LoadingState
import net.kustax.opendelivery.web.ui.SnackbarController
import net.kustax.opendelivery.web.util.formatTimestamp
import net.kustax.opendelivery.web.util.toPlatformLabel

@Composable
fun FoodDeliveryScreen(client: ApiClient) {
    var platforms by remember { mutableStateOf<List<FoodPlatformResponse>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var platformToDisconnect by remember { mutableStateOf<FoodPlatformResponse?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            platforms = client.listFoodPlatforms()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Food delivery", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Connect the delivery platforms your guests can order restaurant food from, then choose which of them " +
                "each property offers when you edit the property. These are emulated integrations: any API key of " +
                "at least 8 characters is accepted and orders are advanced by a simulated platform.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        errorMessage?.let { ErrorBanner(message = it, onDismiss = { errorMessage = null }) }

        val loaded = platforms
        if (loaded == null) {
            LoadingState()
        } else {
            loaded.forEach { platform ->
                FoodPlatformCard(
                    platform = platform,
                    onConnect = { apiKey ->
                        scope.launch {
                            try {
                                client.connectFoodPlatform(platform.platform, apiKey)
                                SnackbarController.show("${platform.platform.toPlatformLabel()} connected")
                                reload()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errorMessage = e.message
                            }
                        }
                    },
                    onDisconnect = { platformToDisconnect = platform }
                )
            }
        }
    }

    platformToDisconnect?.let { platform ->
        ConfirmDialog(
            title = "Disconnect ${platform.platform.toPlatformLabel()}",
            message = "Disconnect ${platform.platform.toPlatformLabel()}? It will also be removed from every property that offers it.",
            confirmLabel = "Disconnect",
            onConfirm = {
                scope.launch {
                    runCatching { client.disconnectFoodPlatform(platform.platform) }.rethrowCancellation()
                        .onSuccess {
                            SnackbarController.show("${platform.platform.toPlatformLabel()} disconnected")
                            reload()
                        }
                        .onFailure { errorMessage = it.message }
                    platformToDisconnect = null
                }
            },
            onDismiss = { platformToDisconnect = null }
        )
    }
}

@Composable
private fun FoodPlatformCard(
    platform: FoodPlatformResponse,
    onConnect: (apiKey: String) -> Unit,
    onDisconnect: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(platform.platform.toPlatformLabel(), style = MaterialTheme.typography.titleMedium)
                    platform.connectedAt?.let {
                        Text(
                            "Connected ${formatTimestamp(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    if (platform.connected) "Connected" else "Not connected",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (platform.connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (platform.connected) "New API key" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConnect(apiKey); apiKey = "" },
                    enabled = apiKey.isNotBlank()
                ) { Text(if (platform.connected) "Replace key" else "Connect") }
                if (platform.connected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) { Text("Disconnect") }
                }
            }
        }
    }
}
