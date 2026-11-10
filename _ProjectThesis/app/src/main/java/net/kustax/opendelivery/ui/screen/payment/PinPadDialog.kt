package net.kustax.opendelivery.ui.screen.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.kustax.opendelivery.ui.theme.AmberPrimary
import net.kustax.opendelivery.ui.theme.ErrorRed
import net.kustax.opendelivery.ui.theme.OnBackground
import net.kustax.opendelivery.ui.theme.OnBackgroundMuted
import net.kustax.opendelivery.ui.theme.OnBackgroundSubtle
import net.kustax.opendelivery.ui.theme.SurfaceDark
import net.kustax.opendelivery.ui.theme.SurfaceElevated
import net.kustax.opendelivery.ui.theme.SurfaceVariant

private const val PIN_LENGTH = 4

// Numeric PIN keypad drawn by the app. The digits live only in this composable's state and are handed to
// onSubmit once; nothing is stored or logged. (The PIN check itself is simulated, see PaymentViewModel.)
@Composable
fun PinPadDialog(onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .background(SurfaceDark, RoundedCornerShape(24.dp))
                .border(1.dp, SurfaceVariant, RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Enter your PIN", style = MaterialTheme.typography.titleLarge, color = OnBackground)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(if (filled) AmberPrimary else Color.Transparent, CircleShape)
                            .border(2.dp, if (filled) AmberPrimary else OnBackgroundSubtle, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("1", "2", "3").let { DigitRow(it, pin.length) { d -> pin += d } }
                listOf("4", "5", "6").let { DigitRow(it, pin.length) { d -> pin += d } }
                listOf("7", "8", "9").let { DigitRow(it, pin.length) { d -> pin += d } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PinKey("Clear", Modifier.size(KEY_WIDTH, KEY_HEIGHT), textColor = OnBackgroundMuted, small = true) { pin = "" }
                    PinKey("0", Modifier.size(KEY_WIDTH, KEY_HEIGHT), enabled = pin.length < PIN_LENGTH) { pin += "0" }
                    PinKey(
                        "OK", Modifier.size(KEY_WIDTH, KEY_HEIGHT),
                        enabled = pin.length == PIN_LENGTH, accent = true, small = true
                    ) { onSubmit(pin) }
                }
            }
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .width(KEY_WIDTH * 3 + 20.dp)
                    .height(48.dp)
                    .border(1.dp, ErrorRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
            }
        }
    }
}

private val KEY_WIDTH = 92.dp
private val KEY_HEIGHT = 64.dp

@Composable
private fun DigitRow(digits: List<String>, length: Int, onDigit: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        digits.forEach { digit ->
            PinKey(digit, Modifier.size(KEY_WIDTH, KEY_HEIGHT), enabled = length < PIN_LENGTH) { onDigit(digit) }
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    small: Boolean = false,
    textColor: Color = OnBackground,
    onClick: () -> Unit
) {
    val background = when {
        accent && enabled -> AmberPrimary
        else -> SurfaceElevated
    }
    val contentColor = when {
        accent && enabled -> Color.Black
        !enabled -> OnBackgroundSubtle
        else -> textColor
    }
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
