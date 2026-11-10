package net.kustax.opendelivery.web.ui

import androidx.compose.runtime.mutableStateOf

object SnackbarController {
    val message = mutableStateOf<String?>(null)
    fun show(msg: String) { message.value = msg }
    fun clear() { message.value = null }
}
