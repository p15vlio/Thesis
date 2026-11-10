@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.kustax.opendelivery.data.response.GeocodedAddressResponse
import net.kustax.opendelivery.web.api.ApiClient
import kotlin.coroutines.resume

// Browsers only expose geolocation on https or localhost; failures are reported through the error callback.
@JsFun(
    """(onSuccess, onError) => {
        if (!navigator.geolocation) { onError('Location is not available in this browser'); return; }
        navigator.geolocation.getCurrentPosition(
            p => onSuccess(p.coords.latitude, p.coords.longitude),
            e => onError(e.code === 1 ? 'Location permission was denied' : 'Could not determine your location'),
            { enableHighAccuracy: true, timeout: 10000 }
        );
    }"""
)
private external fun requestPosition(onSuccess: (Double, Double) -> Unit, onError: (JsString) -> Unit)

private sealed interface Position {
    data class Found(val lat: Double, val lon: Double) : Position
    data class Failed(val message: String) : Position
}

private suspend fun currentPosition(): Position = suspendCancellableCoroutine { continuation ->
    requestPosition(
        onSuccess = { lat, lon -> if (continuation.isActive) continuation.resume(Position.Found(lat, lon)) },
        onError = { message -> if (continuation.isActive) continuation.resume(Position.Failed(message.toString())) }
    )
}

// Explicit search only (Nominatim's usage policy forbids search-as-you-type).
@Composable
fun AddressSearch(
    client: ApiClient,
    latitude: Double?,
    longitude: Double?,
    onPick: (GeocodedAddressResponse) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodedAddressResponse>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            errorMessage = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find address") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { run { results = client.searchAddress(query.trim()) } },
                enabled = !busy && query.trim().length >= 3
            ) { Text("Search") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    run {
                        when (val position = currentPosition()) {
                            is Position.Found -> results = listOf(client.reverseGeocode(position.lat, position.lon))
                            is Position.Failed -> errorMessage = position.message
                        }
                    }
                },
                enabled = !busy
            ) { Text("Use my location") }
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            if (latitude != null && longitude != null) {
                Text(
                    "📍 ${formatCoordinate(latitude)}, ${formatCoordinate(longitude)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        errorMessage?.let { ErrorBanner(message = it, onDismiss = { errorMessage = null }) }
        results?.let { found ->
            if (found.isEmpty()) {
                Text(
                    "No addresses found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        found.forEachIndexed { index, address ->
                            if (index > 0) HorizontalDivider()
                            Text(
                                address.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(address)
                                        results = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
        // Attribution required by the OpenStreetMap data licence and the Nominatim usage policy.
        Text(
            "Address data © OpenStreetMap contributors, search by Nominatim",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCoordinate(value: Double): String {
    val scaled = kotlin.math.round(value * 10_000).toLong()
    val sign = if (scaled < 0) "-" else ""
    val abs = kotlin.math.abs(scaled)
    return "$sign${abs / 10_000}.${(abs % 10_000).toString().padStart(4, '0')}"
}
