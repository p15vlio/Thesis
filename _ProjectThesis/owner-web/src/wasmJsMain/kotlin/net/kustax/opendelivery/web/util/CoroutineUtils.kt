package net.kustax.opendelivery.web.util

import kotlinx.coroutines.CancellationException

// runCatching also catches the CancellationException of a screen that is closing; rethrow it so the
// coroutine really stops instead of handling the cancellation as an ordinary request failure
fun <T> Result<T>.rethrowCancellation(): Result<T> =
    onFailure { if (it is CancellationException) throw it }
