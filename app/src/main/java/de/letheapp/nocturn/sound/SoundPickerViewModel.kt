package de.letheapp.nocturn.sound

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.sound.lethe.LetheMediaApiException
import de.letheapp.nocturn.sound.lethe.LetheMediaRepository
import de.letheapp.nocturn.sound.lethe.LetheMediaTrack
import de.letheapp.nocturn.sound.lethe.LethePairingPollResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAIRING_POLL_INTERVAL_MS = 2000L

sealed class LetheLibraryState {
    data object NotPaired : LetheLibraryState()
    data class AwaitingConfirmation(val requestId: String) : LetheLibraryState()
    data object Loading : LetheLibraryState()
    data class Paired(val tracks: List<LetheMediaTrack>) : LetheLibraryState()
    data class Error(val message: String) : LetheLibraryState()
}

@HiltViewModel
class SoundPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soundRepository: SoundRepository,
    private val letheMediaRepository: LetheMediaRepository
) : ViewModel() {

    private var previewPlayer: MediaPlayer? = null

    /** Spielt den gewählten Ton kurz zur Vorschau ab, bricht eine evtl. laufende Vorschau ab. */
    fun previewSound(uri: String) {
        stopPreview()
        previewPlayer = MediaPlayer().apply {
            runCatching {
                setDataSource(context, Uri.parse(uri))
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopPreview() }
                setOnErrorListener { _, _, _ -> stopPreview(); true }
                prepareAsync()
            }
        }
    }

    fun stopPreview() {
        previewPlayer?.let { player ->
            runCatching { player.stop() }
            player.release()
        }
        previewPlayer = null
    }

    fun bundledSounds(): List<SoundOption> = soundRepository.bundledSounds()

    fun systemSounds(): List<SoundOption> = soundRepository.systemAlarmSounds()

    fun customSounds(): List<SoundOption> = soundRepository.customSounds()

    fun onCustomFilePicked(uri: Uri) {
        soundRepository.takePersistableAccess(uri)
    }

    private val _letheState = MutableStateFlow<LetheLibraryState>(LetheLibraryState.NotPaired)
    val letheState: StateFlow<LetheLibraryState> = _letheState.asStateFlow()

    private var pollJob: Job? = null

    /** Beim ersten Öffnen des Lethe-Tabs: wenn schon gekoppelt, direkt die Bibliothek laden. */
    fun onLetheTabOpened() {
        if (_letheState.value is LetheLibraryState.Paired || _letheState.value is LetheLibraryState.Loading) return
        if (letheMediaRepository.isPaired()) {
            loadLetheLibrary()
        } else {
            _letheState.value = LetheLibraryState.NotPaired
        }
    }

    fun startLethePairing(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _letheState.value = LetheLibraryState.Loading
            try {
                val pairing = letheMediaRepository.startPairing(phoneNumber, password)
                _letheState.value = LetheLibraryState.AwaitingConfirmation(pairing.requestId)
                startPolling(pairing.requestId)
            } catch (e: LetheMediaApiException) {
                _letheState.value = LetheLibraryState.Error(e.message ?: "Kopplung fehlgeschlagen.")
            }
        }
    }

    fun cancelPairing() {
        pollJob?.cancel()
        _letheState.value = LetheLibraryState.NotPaired
    }

    fun loadLetheLibrary() {
        viewModelScope.launch {
            _letheState.value = LetheLibraryState.Loading
            try {
                _letheState.value = LetheLibraryState.Paired(letheMediaRepository.library())
            } catch (e: LetheMediaApiException) {
                _letheState.value = LetheLibraryState.Error(e.message ?: "Bibliothek konnte nicht geladen werden.")
            }
        }
    }

    fun forgetLethePairing() {
        pollJob?.cancel()
        letheMediaRepository.forgetPairing()
        _letheState.value = LetheLibraryState.NotPaired
    }

    private fun startPolling(requestId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(PAIRING_POLL_INTERVAL_MS)
                try {
                    when (val result = letheMediaRepository.pollPairing(requestId)) {
                        is LethePairingPollResult.Approved -> {
                            loadLetheLibrary()
                            return@launch
                        }
                        LethePairingPollResult.Denied -> {
                            _letheState.value = LetheLibraryState.Error("Die Kopplung wurde auf deinem Lethe-Gerät abgelehnt.")
                            return@launch
                        }
                        LethePairingPollResult.Expired -> {
                            _letheState.value = LetheLibraryState.Error("Anfrage abgelaufen. Bitte erneut koppeln.")
                            return@launch
                        }
                        LethePairingPollResult.Pending -> { /* weiter pollen */ }
                    }
                } catch (e: LetheMediaApiException) {
                    _letheState.value = LetheLibraryState.Error(e.message ?: "Fehler bei der Kopplung.")
                    return@launch
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        stopPreview()
    }
}
