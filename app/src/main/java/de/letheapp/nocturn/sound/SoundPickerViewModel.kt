package de.letheapp.nocturn.sound

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SoundPickerViewModel @Inject constructor(
    private val soundRepository: SoundRepository
) : ViewModel() {

    fun systemSounds(): List<SoundOption> = soundRepository.systemAlarmSounds()

    fun customSounds(): List<SoundOption> = soundRepository.customSounds()

    fun onCustomFilePicked(uri: Uri) {
        soundRepository.takePersistableAccess(uri)
    }
}
