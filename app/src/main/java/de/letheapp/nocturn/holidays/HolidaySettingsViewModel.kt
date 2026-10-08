package de.letheapp.nocturn.holidays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.HolidaySettingsEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HolidaySettingsViewModel @Inject constructor(
    private val holidayRepository: HolidayRepository
) : ViewModel() {

    val settings: StateFlow<HolidaySettingsEntity?> = holidayRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * [onSaved] wird erst nach Abschluss des DB-Schreibvorgangs aufgerufen — ruft der Screen
     * stattdessen sofort nach dem Start von save() ein popBackStack() auf, wird dieser
     * ViewModel-Scope abgebrochen, bevor die suspend-DB-Operation überhaupt läuft, und die
     * Einstellung geht verloren (Ursache des Bugs "Feiertage werden trotz Auswahl nicht angezeigt").
     */
    fun save(state: GermanState, showPublicHolidays: Boolean, showSchoolHolidays: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            holidayRepository.saveSettings(state.subdivisionCode, state.displayName, showPublicHolidays, showSchoolHolidays)
            onSaved()
        }
    }
}
