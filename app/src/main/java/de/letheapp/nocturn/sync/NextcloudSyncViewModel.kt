package de.letheapp.nocturn.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.CalDavAccountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data object Loading : DiscoveryState
    data class Success(val calendars: List<CalDavCalendarInfo>) : DiscoveryState
    data class Error(val message: String) : DiscoveryState
}

/** `null` = noch nicht geprüft/unbekannt (z. B. während der Prüfung oder ohne Verbindung). */
sealed interface NocturnKalenderAvailability {
    data object Unknown : NocturnKalenderAvailability
    data object Checking : NocturnKalenderAvailability
    data object Available : NocturnKalenderAvailability
    data object NotAvailable : NocturnKalenderAvailability
}

@HiltViewModel
class NextcloudSyncViewModel @Inject constructor(
    private val syncRepository: NextcloudSyncRepository
) : ViewModel() {

    val account: StateFlow<CalDavAccountEntity?> = syncRepository.observeAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _nocturnKalenderAvailability =
        MutableStateFlow<NocturnKalenderAvailability>(NocturnKalenderAvailability.Unknown)
    val nocturnKalenderAvailability: StateFlow<NocturnKalenderAvailability> = _nocturnKalenderAvailability

    private var pendingServerUrl: String = ""
    private var pendingUsername: String = ""
    private var pendingPassword: String = ""

    fun discover(serverUrl: String, username: String, password: String) {
        pendingServerUrl = serverUrl
        pendingUsername = username
        pendingPassword = password
        _discoveryState.value = DiscoveryState.Loading
        viewModelScope.launch {
            _discoveryState.value = try {
                val calendars = syncRepository.discoverCalendars(serverUrl, username, password)
                if (calendars.isEmpty()) {
                    DiscoveryState.Error("Keine Kalender auf diesem Server gefunden")
                } else {
                    DiscoveryState.Success(calendars)
                }
            } catch (e: Exception) {
                DiscoveryState.Error(e.message ?: "Verbindung fehlgeschlagen")
            }
        }
        recheckNocturnKalenderAvailability()
    }

    /**
     * Prüft erneut, ob die NocTurn-Kalender-App verfügbar ist — sowohl für den bereits
     * verbundenen Zustand (Passwort kommt dann aus dem verschlüsselten Store) als auch während
     * des Verbinden-Formulars (Passwort noch nicht gespeichert, kommt aus den Eingabefeldern).
     * Wird beim Betreten des Screens sowie über einen "Erneut prüfen"-Button aufgerufen.
     */
    fun recheckNocturnKalenderAvailability() {
        val isConnected = account.value != null
        if (!isConnected && (pendingServerUrl.isBlank() || pendingUsername.isBlank() || pendingPassword.isBlank())) return
        _nocturnKalenderAvailability.value = NocturnKalenderAvailability.Checking
        viewModelScope.launch {
            val available = runCatching {
                if (isConnected) {
                    syncRepository.checkNocturnKalenderAvailableForConnectedAccount()
                } else {
                    syncRepository.checkNocturnKalenderAvailable(pendingServerUrl, pendingUsername, pendingPassword)
                }
            }.getOrDefault(false)
            _nocturnKalenderAvailability.value =
                if (available) NocturnKalenderAvailability.Available else NocturnKalenderAvailability.NotAvailable
        }
    }

    fun resetDiscovery() {
        _discoveryState.value = DiscoveryState.Idle
    }

    fun connect(calendar: CalDavCalendarInfo, syncAppointments: Boolean, syncNocturnSchedule: Boolean) {
        viewModelScope.launch {
            syncRepository.connect(pendingServerUrl, pendingUsername, pendingPassword, calendar, syncAppointments, syncNocturnSchedule)
            _discoveryState.value = DiscoveryState.Idle
            syncNow()
        }
    }

    fun updateSyncOptions(syncAppointments: Boolean, syncNocturnSchedule: Boolean) {
        val current = account.value ?: return
        viewModelScope.launch { syncRepository.updateSyncOptions(current, syncAppointments, syncNocturnSchedule) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            syncRepository.syncNow()
            _isSyncing.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch { syncRepository.disconnect() }
    }
}
