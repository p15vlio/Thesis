package net.kustax.opendelivery.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestDeskTopBar(
    title: String,
    cartItemCount: Int = 0,
    showBack: Boolean = false,
    showCart: Boolean = true,
    showInfo: Boolean = false,
    onBack: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onTrackClick: (() -> Unit)? = null,
    onAdminGesture: () -> Unit = {}
) {
    val tapTimestamps = remember { mutableListOf<Long>() }

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = OnBackground
            )
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OnBackground
                    )
                }
            }
        },
        actions = {
            if (onTrackClick != null) {
                TextButton(onClick = onTrackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Track order",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberPrimary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (showInfo) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = OnBackground
                    )
                }
            }
            if (showCart) {
                CartBadge(count = cartItemCount, onClick = onCartClick)
            }
            // Invisible admin gesture zone — 5 taps within 3 seconds triggers admin access
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            val now = System.currentTimeMillis()
                            tapTimestamps.add(now)
                            tapTimestamps.removeAll { now - it > 3000 }
                            if (tapTimestamps.size >= 5) {
                                tapTimestamps.clear()
                                onAdminGesture()
                            }
                        }
                    }
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceDark,
            titleContentColor = OnBackground
        )
    )
}
