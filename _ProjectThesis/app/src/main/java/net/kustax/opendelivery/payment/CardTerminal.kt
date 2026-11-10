package net.kustax.opendelivery.payment

// What the guest did with the card reader
sealed interface CardEvent {
    data object Tapped : CardEvent
    data object Inserted : CardEvent
    // last4 is the end of the card number read from the stripe, null when it could not be parsed
    data class Swiped(val last4: String?) : CardEvent
    // The reader answered but could not read the card; the guest can simply try again
    data object ReadFailed : CardEvent
    // The card reader service is missing or not responding
    data object Unavailable : CardEvent
}

interface CardTerminal {
    // Suspends until the guest taps, inserts or swipes a card. Cancelling the caller cancels the wait on the reader.
    suspend fun awaitCard(): CardEvent

    // Releases the reader connection; awaitCard reconnects on demand
    fun release()
}
