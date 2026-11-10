@file:OptIn(ExperimentalWasmJsInterop::class)

package net.kustax.opendelivery.web.util

import kotlin.js.ExperimentalWasmJsInterop

fun String.toLabel(): String =
    split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }

// "yyyy-MM-dd HH:mm" in the browser's local time zone
fun formatTimestamp(epochMs: Long): String = jsFormatTimestamp(epochMs.toDouble())

@JsFun(
    """(ms) => {
        const d = new Date(ms);
        const p = (n) => String(n).padStart(2, '0');
        return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
    }"""
)
private external fun jsFormatTimestamp(epochMs: Double): String

fun String.entryModeLabel(): String = when (this) {
    "CARD_CONTACTLESS" -> "Contactless"
    "CARD_CHIP" -> "Chip & PIN"
    "CARD_MAGSTRIPE" -> "Magstripe"
    else -> toLabel()
}
