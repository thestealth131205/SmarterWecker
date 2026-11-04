package de.letheapp.nocturn.iobroker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.IoBrokerAccountEntity
import de.letheapp.nocturn.onboarding.AppPreferences
import de.letheapp.nocturn.onboarding.HomeAssistantConfig
import de.letheapp.nocturn.onboarding.SmartHomeBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface IoBrokerConnectState {
    data object Idle : IoBrokerConnectState
    data object Loading : IoBrokerConnectState
    data object Error : IoBrokerConnectState
}

@HiltViewModel
class IoBrokerSettingsViewModel @Inject constructor(
    private val repository: IoBrokerRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val backend: StateFlow<SmartHomeBackend> = appPreferences.smartHomeBackend

    val homeAssistantConfig: StateFlow<HomeAssistantConfig?> = appPreferences.homeAssistantConfig

    fun setBackend(backend: SmartHomeBackend) {
        _connectState.value = IoBrokerConnectState.Idle
        appPreferences.setSmartHomeBackend(backend)
    }

    fun connectHomeAssistant(host: String, port: Int, useHttps: Boolean, token: String) {
        _connectState.value = IoBrokerConnectState.Loading
        viewModelScope.launch {
            if (repository.testHomeAssistant(host, port, useHttps, token)) {
                repository.connectHomeAssistant(host, port, useHttps, token)
                _connectState.value = IoBrokerConnectState.Idle
            } else {
                _connectState.value = IoBrokerConnectState.Error
            }
        }
    }

    fun disconnectHomeAssistant() {
        repository.disconnectHomeAssistant()
    }

    val account: StateFlow<IoBrokerAccountEntity?> = repository.observeAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _connectState = MutableStateFlow<IoBrokerConnectState>(IoBrokerConnectState.Idle)
    val connectState: StateFlow<IoBrokerConnectState> = _connectState

    fun connect(host: String, port: Int, useHttps: Boolean, username: String, password: String) {
        _connectState.value = IoBrokerConnectState.Loading
        viewModelScope.launch {
            val reachable = repository.testConnection(host, port, useHttps, username, password)
            if (reachable) {
                repository.connect(host, port, useHttps, username, password)
                _connectState.value = IoBrokerConnectState.Idle
            } else {
                _connectState.value = IoBrokerConnectState.Error
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch { repository.disconnect() }
    }
}
