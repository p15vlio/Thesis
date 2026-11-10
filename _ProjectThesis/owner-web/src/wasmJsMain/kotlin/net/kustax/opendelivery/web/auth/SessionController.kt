package net.kustax.opendelivery.web.auth

import androidx.compose.runtime.mutableStateOf

object SessionController {
    val expired = mutableStateOf(false)

    fun markExpired() {
        expired.value = true
    }

    fun reset() {
        expired.value = false
    }
}
