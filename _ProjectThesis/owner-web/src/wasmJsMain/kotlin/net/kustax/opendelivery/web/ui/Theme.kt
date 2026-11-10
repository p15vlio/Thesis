package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Premium navy-teal SaaS palette
private val NavyPrimary     = Color(0xFF1E3A5F)
private val NavyOnPrimary   = Color(0xFFFFFFFF)
private val TealSecondary   = Color(0xFF0D9488)
private val SlateBackground = Color(0xFFF1F5F9)   // slate-100 — more distinct from white surface
private val SurfaceWhite    = Color(0xFFFFFFFF)
private val SurfaceVariant  = Color(0xFFE2E8F0)   // slate-200 — more distinct from white
private val OutlineColor    = Color(0xFF94A3B8)   // slate-400 — much stronger than before
private val OnSurface       = Color(0xFF0F172A)
private val OnSurfaceVar    = Color(0xFF475569)   // slate-600 — more readable than 64748B
private val TertiaryGreen   = Color(0xFF059669)
private val ErrorRed        = Color(0xFFDC2626)

private val LightColorScheme = lightColorScheme(
    primary              = NavyPrimary,
    onPrimary            = NavyOnPrimary,
    primaryContainer     = Color(0xFFDCEAFF),
    onPrimaryContainer   = Color(0xFF0A1929),
    secondary            = TealSecondary,
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary             = TertiaryGreen,
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFD1FAE5),
    onTertiaryContainer  = Color(0xFF022C22),
    error                = ErrorRed,
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFFFEE2E2),
    onErrorContainer     = Color(0xFF450A0A),
    background           = SlateBackground,
    onBackground         = OnSurface,
    surface              = SurfaceWhite,
    onSurface            = OnSurface,
    surfaceVariant       = SurfaceVariant,
    onSurfaceVariant     = OnSurfaceVar,
    outline              = OutlineColor,
    outlineVariant       = Color(0xFF94A3B8),   // slate-400 — strong visible borders
    inverseSurface       = Color(0xFF1E293B),
    inverseOnSurface     = Color(0xFFF1F5F9)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small       = RoundedCornerShape(8.dp),
    medium      = RoundedCornerShape(12.dp),
    large       = RoundedCornerShape(16.dp),
    extraLarge  = RoundedCornerShape(20.dp)
)

@Composable
fun OpenDeliveryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
