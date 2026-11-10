package net.kustax.opendelivery.web.util

// String.format is JVM-only; this is the Kotlin/Wasm-safe equivalent
fun formatEuros(cents: Long): String {
    val whole = cents / 100L
    val fraction = (cents % 100L).toInt()
    return "€$whole.${fraction.toString().padStart(2, '0')}"
}

// For pre-filling price input fields
fun centsToInputString(cents: Long): String {
    val whole = cents / 100L
    val fraction = (cents % 100L).toInt()
    return "$whole.${fraction.toString().padStart(2, '0')}"
}
