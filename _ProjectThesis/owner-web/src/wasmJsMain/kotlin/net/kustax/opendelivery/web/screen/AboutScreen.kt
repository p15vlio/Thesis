package net.kustax.opendelivery.web.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kustax.opendelivery.web.api.ApiClient

@Composable
fun AboutScreen(client: ApiClient? = null) {
    var backendOnline by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        backendOnline = client?.checkHealth() ?: false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Guest Desk", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Management Console",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        AboutSection("System")
        InfoRow("Version", "1.0.0")
        InfoRow("Platform", "Compose Multiplatform (wasmJs)")
        InfoRow("Language", "Kotlin 2.4.21")
        InfoRow("UI Framework", "Compose Multiplatform 1.12.0")
        InfoRow("Backend", ApiClient.BASE_URL.ifEmpty { "Same origin (/api)" })

        // Health indicator row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Backend Status",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.35f)
            )
            Row(
                modifier = Modifier.weight(0.65f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val dotColor = when (backendOnline) {
                    true -> Color(0xFF2E7D32)
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = CircleShape,
                    color = dotColor,
                    modifier = Modifier.size(10.dp)
                ) {}
                Text(
                    when (backendOnline) {
                        true -> "Backend online"
                        false -> "Backend offline"
                        null -> "Checking..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        AboutSection("Thesis")
        InfoRow("Title", "Design and Implementation of a Digital Ordering and Services System for Short-Term Rental Accommodations")
        InfoRow("Student", "Konstantinos Vlioras — Π2015102")
        InfoRow("School", "Ionian University")
        InfoRow("Type", "Bachelor's Thesis")

        HorizontalDivider()

        AboutSection("Support")
        InfoRow("Developer", "Konstantinos Vlioras")
        InfoRow("Email", "p15vlio@ionio.gr")
        InfoRow("Company", "GuestDesk S.A.")

        HorizontalDivider()

        AboutSection("Copyright")
        InfoRow("", "© 2026 Konstantinos Vlioras. All rights reserved.")
    }
}

@Composable
private fun AboutSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.65f)
        )
    }
}
