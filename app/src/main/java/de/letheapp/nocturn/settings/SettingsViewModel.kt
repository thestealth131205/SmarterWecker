package de.letheapp.nocturn.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.onboarding.AppPreferences
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    val dynamicColorEnabled: StateFlow<Boolean> = appPreferences.dynamicColorEnabled

    fun setDynamicColorEnabled(enabled: Boolean) {
        appPreferences.setDynamicColorEnabled(enabled)
    }
}
