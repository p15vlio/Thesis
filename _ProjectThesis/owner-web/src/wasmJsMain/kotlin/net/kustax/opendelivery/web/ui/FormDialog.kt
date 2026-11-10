package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun FormDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmLabel: String = "Confirm",
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
            modifier = Modifier
                .widthIn(min = 480.dp, max = 680.dp)
                .fillMaxHeight(0.85f)
        ) {
            Column {
                // Sticky header
                Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 0.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                                if (event.isShiftPressed) focusManager.moveFocus(FocusDirection.Previous)
                                else focusManager.moveFocus(FocusDirection.Next)
                                true
                            } else false
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }

                // Sticky footer
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}
