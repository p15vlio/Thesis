package net.kustax.opendelivery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KioskColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color(0xFF000000),
    primaryContainer = AmberDark,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = AmberLight,
    onSecondary = Color(0xFF000000),
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceDark,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnBackgroundMuted,
    error = ErrorRed,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun GuestDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KioskColorScheme,
        typography = GuestDeskTypography,
        content = content
    )
}
