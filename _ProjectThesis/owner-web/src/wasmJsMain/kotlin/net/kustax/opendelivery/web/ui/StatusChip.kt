package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kustax.opendelivery.web.util.toLabel

private data class ChipStyle(val bg: Color, val fg: Color, val dot: Color)

@Composable
private fun statusStyle(status: String): ChipStyle = when (status) {
    "PENDING"          -> ChipStyle(Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFFF59E0B))
    "CONFIRMED"        -> ChipStyle(Color(0xFFDBEAFE), Color(0xFF1E3A8A), Color(0xFF3B82F6))
    "PREPARING"        -> ChipStyle(Color(0xFFEDE9FE), Color(0xFF4C1D95), Color(0xFF8B5CF6))
    "OUT_FOR_DELIVERY" -> ChipStyle(Color(0xFFE0F2FE), Color(0xFF0C4A6E), Color(0xFF0284C7))
    "DELIVERED"        -> ChipStyle(Color(0xFFD1FAE5), Color(0xFF064E3B), Color(0xFF10B981))
    "CANCELLED"        -> ChipStyle(Color(0xFFFEE2E2), Color(0xFF7F1D1D), Color(0xFFEF4444))
    else               -> ChipStyle(Color(0xFFF1F5F9), Color(0xFF475569), Color(0xFF94A3B8))
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val style = statusStyle(status)
    Row(
        modifier = modifier
            .background(style.bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).background(style.dot, CircleShape))
        Text(
            status.toLabel(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = style.fg
        )
    }
}

@Composable
fun ActiveBadge(isActive: Boolean, modifier: Modifier = Modifier) {
    val (bg, fg, dot) = if (isActive)
        Triple(Color(0xFFD1FAE5), Color(0xFF064E3B), Color(0xFF10B981))
    else
        Triple(Color(0xFFFEE2E2), Color(0xFF7F1D1D), Color(0xFFEF4444))

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(
            if (isActive) "Active" else "Inactive",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg
        )
    }
}
