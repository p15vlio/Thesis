package net.kustax.opendelivery.ui.screen.idle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kustax.opendelivery.AppContainer
import net.kustax.opendelivery.model.UiProperty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IdleViewModel : ViewModel() {

    val property: StateFlow<UiProperty?> = AppContainer.propertyRepository.property

    private val _currentTime = MutableStateFlow(formatTime())
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    init {
        // Idle marks the end of a guest visit, so the next guest starts without the previous guest's data
        viewModelScope.launch { AppContainer.endGuestSession() }
        startClock()
    }

    private fun startClock() {
        viewModelScope.launch {
            while (isActive) {
                _currentTime.value = formatTime()
                delay(30_000L)
            }
        }
    }

    private fun formatTime(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
