package net.kustax.opendelivery.web.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Disabling the button here (rather than relying on every caller to clear dialog state before
    // launching its network call) closes the double-submit race regardless of when each caller happens
    // to clear its "itemToX" state relative to the async work completing.
    var isConfirming by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!isConfirming) {
                        isConfirming = true
                        onConfirm()
                    }
                },
                enabled = !isConfirming
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConfirming) { Text("Cancel") }
        }
    )
}
