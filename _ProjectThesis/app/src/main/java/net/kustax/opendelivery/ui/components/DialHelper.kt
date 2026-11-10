package net.kustax.opendelivery.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import net.kustax.opendelivery.ui.theme.AmberPrimary

// Opens the dialer for a number. The kiosk tablet has no phone app, so when nothing handles the
// intent the number is shown in a dialog instead, to be dialed from the guest's own phone.
@Stable
class DialState(private val context: Context) {
    var fallbackNumber by mutableStateOf<String?>(null)
        private set

    fun dial(number: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
        } catch (_: ActivityNotFoundException) {
            fallbackNumber = number
        }
    }

    fun dismiss() {
        fallbackNumber = null
    }
}

@Composable
fun rememberDialState(): DialState {
    val context = LocalContext.current
    return remember(context) { DialState(context) }
}

@Composable
fun DialFallbackDialog(state: DialState) {
    val number = state.fallbackNumber ?: return
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text("Call from your phone") },
        text = {
            Text(
                text = number,
                style = MaterialTheme.typography.headlineLarge,
                color = AmberPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = state::dismiss) { Text("OK") }
        }
    )
}
