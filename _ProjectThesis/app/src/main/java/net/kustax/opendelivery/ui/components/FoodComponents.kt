package net.kustax.opendelivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Brand-like colours, text only (no trademark logos)
fun platformColor(platform: String): Color = when (platform) {
    "EFOOD" -> Color(0xFFED1C24)
    "WOLT" -> Color(0xFF009DE0)
    "BOX" -> Color(0xFF7C3AED)
    else -> Color(0xFF525252)
}

fun platformLabel(platform: String): String = when (platform) {
    "EFOOD" -> "efood"
    "WOLT" -> "Wolt"
    "BOX" -> "Box"
    else -> platform
}

@Composable
fun PlatformBadge(platform: String, modifier: Modifier = Modifier) {
    Text(
        text = platformLabel(platform),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .background(platformColor(platform), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

fun formatEuro(cents: Long): String = "€${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
