package net.kustax.opendelivery.ui.screen.idle

import android.app.Activity
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import net.kustax.opendelivery.kiosk.KioskManager
import net.kustax.opendelivery.navigation.Screen
import net.kustax.opendelivery.ui.components.AdminPinDialog
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.Background
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.OnBackgroundSubtle

@Composable
fun IdleScreen(navController: NavController) {
    val vm: IdleViewModel = viewModel()
    val property by vm.property.collectAsState()
    val currentTime by vm.currentTime.collectAsState()
    val context = LocalContext.current
    var showAdminDialog by remember { mutableStateOf(false) }
    val tapTimestamps = remember { mutableListOf<Long>() }

    if (showAdminDialog) {
        AdminPinDialog(
            onConfirm = {
                showAdminDialog = false
                (context as? Activity)?.let { activity ->
                    KioskManager(activity).stopKiosk()
                    activity.finish()
                }
            },
            onDismiss = { showAdminDialog = false }
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1C1C1C), Background),
                    radius = 1200f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Idle.route) { inclusive = true }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = currentTime,
            style = MaterialTheme.typography.titleLarge,
            color = OnBackgroundMuted,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
        )

        // Invisible admin gesture zone — 5 taps within 3 seconds triggers admin access
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(56.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        val now = System.currentTimeMillis()
                        tapTimestamps.add(now)
                        tapTimestamps.removeAll { now - it > 3000 }
                        if (tapTimestamps.size >= 5) {
                            tapTimestamps.clear()
                            showAdminDialog = true
                        }
                    }
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(AmberPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            property?.let {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnBackground,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "Guest Desk",
                style = MaterialTheme.typography.titleLarge,
                color = OnBackgroundMuted
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Tap anywhere to start",
                style = MaterialTheme.typography.bodyLarge,
                color = OnBackgroundSubtle,
                textAlign = TextAlign.Center
            )
        }
    }
}
