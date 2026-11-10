package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.kustax.opendelivery.web.util.toPlatformLabel

// Checkbox per platform the owner has connected; used inside the property create/edit dialogs
@Composable
fun FoodPlatformPicker(available: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Text("Food delivery platforms", style = MaterialTheme.typography.labelLarge)
    if (available.isEmpty()) {
        Text(
            "No platform connected yet. Connect one under Food delivery to offer it at a property.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            available.forEach { platform ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = platform in selected,
                        onCheckedChange = { checked -> onChange(if (checked) selected + platform else selected - platform) }
                    )
                    Text(platform.toPlatformLabel(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// Small pills listing the platforms enabled at a property
@Composable
fun FoodPlatformBadges(platforms: List<String>) {
    if (platforms.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        platforms.forEach { platform ->
            Text(
                platform.toPlatformLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
