package net.kustax.opendelivery.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kustax.opendelivery.model.UiProperty

// Holds the active property for the current session. Null until a property fetch from the backend
// succeeds (see AppContainer.refreshProperty and RegistrationViewModel).
class MutablePropertyRepository {

    private val _property = MutableStateFlow<UiProperty?>(null)
    val property: StateFlow<UiProperty?> = _property.asStateFlow()

    fun replace(property: UiProperty) {
        _property.value = property
    }
}
