package de.letheapp.nocturn.onboarding

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nocturn_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_done"
private const val KEY_WEATHER_ENABLED = "weather_enabled"
private const val KEY_LOCATION_NAME = "weather_location_name"
private const val KEY_LOCATION_LAT = "weather_location_lat"
private const val KEY_LOCATION_LON = "weather_location_lon"
private const val KEY_LIVE_LOCATION_ENABLED = "weather_live_location_enabled"
private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"

/**
 * Merkt sich, ob das Berechtigungen-Onboarding (siehe CLAUDE.md Kernfunktion 10) bereits lief,
 * sowie weitere, über den neuen "Einstellungen"-Screen umschaltbare App-weite Optionen.
 */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    /** Standardmäßig aus: Wetteranzeige (Topbar-Symbol + Uhr-Widget) ist ein bewusstes Opt-in. */
    private val _weatherEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEATHER_ENABLED, false))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled

    fun isWeatherEnabled(): Boolean = _weatherEnabled.value

    fun setWeatherEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply()
        _weatherEnabled.value = enabled
    }

    /**
     * Standardmäßig aus: Statt der letzten vom System zwischengespeicherten Position (kann
     * veraltet sein) fordert [de.letheapp.nocturn.weather.WeatherRepository] bei jeder
     * Wetter-Aktualisierung aktiv eine frische, ungefähre Standortermittlung an. Bewusstes
     * Opt-in, da eine aktive Standortanfrage kurzzeitig mehr Akku braucht als das bloße
     * Auslesen der letzten bekannten Position. Wirkt nur ohne manuell gewählten Ort.
     */
    private val _liveLocationEnabled = MutableStateFlow(prefs.getBoolean(KEY_LIVE_LOCATION_ENABLED, false))
    val liveLocationEnabled: StateFlow<Boolean> = _liveLocationEnabled

    fun isLiveLocationEnabled(): Boolean = _liveLocationEnabled.value

    fun setLiveLocationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_LOCATION_ENABLED, enabled).apply()
        _liveLocationEnabled.value = enabled
    }

    /**
     * Standardmäßig aus: NocTurn nutzt ein festes Neon-Gelb-Branding (siehe
     * [de.letheapp.nocturn.ui.theme.NocTurnTheme]) statt der vom Geräte-Wallpaper abgeleiteten
     * Material-You-Akzentfarbe. Wer das möchte, kann Dynamic Color hier wieder einschalten.
     */
    private val _dynamicColorEnabled = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, false))
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR_ENABLED, enabled).apply()
        _dynamicColorEnabled.value = enabled
    }

    /** Manuell gewählter Wetter-Ort statt der automatischen `LocationManager`-Ermittlung. */
    data class ManualLocation(val name: String, val latitude: Double, val longitude: Double)

    private val _manualLocation = MutableStateFlow(loadManualLocation())
    val manualLocation: StateFlow<ManualLocation?> = _manualLocation

    private fun loadManualLocation(): ManualLocation? {
        val name = prefs.getString(KEY_LOCATION_NAME, null) ?: return null
        val lat = prefs.getString(KEY_LOCATION_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_LOCATION_LON, null)?.toDoubleOrNull() ?: return null
        return ManualLocation(name, lat, lon)
    }

    fun setManualLocation(location: ManualLocation) {
        prefs.edit()
            .putString(KEY_LOCATION_NAME, location.name)
            .putString(KEY_LOCATION_LAT, location.latitude.toString())
            .putString(KEY_LOCATION_LON, location.longitude.toString())
            .apply()
        _manualLocation.value = location
    }

    fun clearManualLocation() {
        prefs.edit()
            .remove(KEY_LOCATION_NAME)
            .remove(KEY_LOCATION_LAT)
            .remove(KEY_LOCATION_LON)
            .apply()
        _manualLocation.value = null
    }
}
