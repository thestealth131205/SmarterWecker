package de.letheapp.nocturn.onboarding

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nocturn_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_done"

/** Merkt sich, ob das Berechtigungen-Onboarding (siehe CLAUDE.md Kernfunktion 10) bereits lief. */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }
}
