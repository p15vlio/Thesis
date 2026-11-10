package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CHECK_OUT_TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

// Blank means "not set", which the backend accepts
fun isValidCheckOutTime(value: String): Boolean = value.isBlank() || CHECK_OUT_TIME_REGEX.matches(value.trim())

// Form section for the information the kiosk shows to guests on its Info screen. All fields are optional.
@Composable
fun GuestInfoFields(
    wifiName: String,
    onWifiNameChange: (String) -> Unit,
    wifiPassword: String,
    onWifiPasswordChange: (String) -> Unit,
    checkOutTime: String,
    onCheckOutTimeChange: (String) -> Unit,
    houseRules: String,
    onHouseRulesChange: (String) -> Unit
) {
    Spacer(Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            "GUEST INFORMATION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = wifiName,
            onValueChange = { if (it.length <= 128) onWifiNameChange(it) },
            label = { Text("Wi-Fi name (optional)") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = wifiPassword,
            onValueChange = { if (it.length <= 128) onWifiPasswordChange(it) },
            label = { Text("Wi-Fi password (optional)") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    OutlinedTextField(
        value = checkOutTime,
        onValueChange = { if (it.length <= 5) onCheckOutTimeChange(it) },
        label = { Text("Check-out time (HH:mm, optional)") },
        placeholder = { Text("11:00") },
        isError = !isValidCheckOutTime(checkOutTime),
        supportingText = { if (!isValidCheckOutTime(checkOutTime)) Text("Use 24-hour HH:mm, for example 11:00") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = houseRules,
        onValueChange = { if (it.length <= 4000) onHouseRulesChange(it) },
        label = { Text("House rules (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8
    )
}
