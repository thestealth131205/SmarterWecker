package de.letheapp.nocturn.onboarding

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
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
private const val KEY_ADDITIONAL_LOCATIONS = "weather_additional_locations"
private const val KEY_VOICE_COMMANDS_ENABLED = "voice_commands_enabled"
private const val KEY_WEATHER_RAIN_NOTIFIED_HOUR = "weather_rain_notified_hour"
private const val KEY_WEATHER_MORNING_NOTIFIED_DAY = "weather_morning_notified_day"
private const val KEY_SMART_HOME_BACKEND = "smart_home_backend"
private const val KEY_HA_HOST = "home_assistant_host"
private const val KEY_HA_PORT = "home_assistant_port"
private const val KEY_HA_USE_HTTPS = "home_assistant_use_https"

/** Welches Smart-Home-System die App-weiten Notfall-Alarm-Aktionen ansteuert. */
enum class SmartHomeBackend { IOBROKER, HOME_ASSISTANT }

/**
 * Verbindungsdaten zu Home Assistant (ohne Token — der liegt verschlüsselt im
 * [de.letheapp.nocturn.iobroker.IoBrokerCredentialsStore]).
 */
data class HomeAssistantConfig(val host: String, val port: Int, val useHttps: Boolean)

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

    /**
     * Standardmäßig an: Mikrofon-Symbol in der Wecker-Titelleiste für Sprachbefehle
     * (Timer/Stoppuhr/einmaliger Wecker, siehe [de.letheapp.nocturn.voice.VoiceCommandParser]).
     * Nutzt nur Androids Board-Mittel-Spracherkennung, keinen eigenen Server/LLM-Aufruf — wer
     * das Mikrofon-Symbol trotzdem nicht sehen möchte, kann es hier ausblenden.
     */
    private val _voiceCommandsEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_COMMANDS_ENABLED, true))
    val voiceCommandsEnabled: StateFlow<Boolean> = _voiceCommandsEnabled

    fun setVoiceCommandsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_COMMANDS_ENABLED, enabled).apply()
        _voiceCommandsEnabled.value = enabled
    }

    /**
     * Dedup-Zustand für [de.letheapp.nocturn.weather.WeatherNotificationWorker]: verhindert, dass
     * dieselbe Regen-Stunde bzw. derselbe Tag mehrfach benachrichtigt wird, da der Worker alle 15
     * Minuten läuft.
     */
    fun getLastRainNotifiedHour(): Long = prefs.getLong(KEY_WEATHER_RAIN_NOTIFIED_HOUR, 0L)

    fun setLastRainNotifiedHour(epochMillis: Long) {
        prefs.edit().putLong(KEY_WEATHER_RAIN_NOTIFIED_HOUR, epochMillis).apply()
    }

    fun getLastMorningNotifiedDay(): Long = prefs.getLong(KEY_WEATHER_MORNING_NOTIFIED_DAY, 0L)

    fun setLastMorningNotifiedDay(epochDay: Long) {
        prefs.edit().putLong(KEY_WEATHER_MORNING_NOTIFIED_DAY, epochDay).apply()
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

    /**
     * Zusätzliche, frei benannte Wetter-Orte (neben dem Hauptort aus [manualLocation]/
     * [liveLocationEnabled]) — im Wetter-Screen per Links/Rechts-Wischen durchblätterbar, siehe
     * `LocationListScreen`. Jeder Ort bekommt eine stabile [SavedLocation.id], damit Widgets
     * (siehe `ClockWidgetConfigureActivity`) und der Wetter-Cache ([WeatherRepository]) ihn
     * eindeutig referenzieren können, auch wenn der Nutzer ihn später umbenennt.
     */
    data class SavedLocation(val id: String, val name: String, val latitude: Double, val longitude: Double)

    private val _additionalLocations = MutableStateFlow(loadAdditionalLocations())
    val additionalLocations: StateFlow<List<SavedLocation>> = _additionalLocations

    private fun loadAdditionalLocations(): List<SavedLocation> {
        val json = prefs.getString(KEY_ADDITIONAL_LOCATIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                SavedLocation(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lon")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAdditionalLocations(locations: List<SavedLocation>) {
        val array = JSONArray()
        locations.forEach { location ->
            array.put(
                JSONObject()
                    .put("id", location.id)
                    .put("name", location.name)
                    .put("lat", location.latitude)
                    .put("lon", location.longitude)
            )
        }
        prefs.edit().putString(KEY_ADDITIONAL_LOCATIONS, array.toString()).apply()
    }

    fun addAdditionalLocation(name: String, latitude: Double, longitude: Double): SavedLocation {
        val location = SavedLocation(UUID.randomUUID().toString(), name, latitude, longitude)
        val updated = _additionalLocations.value + location
        writeAdditionalLocations(updated)
        _additionalLocations.value = updated
        return location
    }

    fun removeAdditionalLocation(id: String) {
        val updated = _additionalLocations.value.filterNot { it.id == id }
        writeAdditionalLocations(updated)
        _additionalLocations.value = updated
    }

    /**
     * Standardmäßig ioBroker: schaltet App-weit um, ob die Notfall-Alarm-Aktionen (Lampe/Rollo/
     * Skript) über den "iosync"-ioBroker-Adapter oder über Home Assistant laufen — gleiches
     * Prinzip wie in der Iosync-App.
     */
    private val _smartHomeBackend = MutableStateFlow(
        if (prefs.getString(KEY_SMART_HOME_BACKEND, null) == SmartHomeBackend.HOME_ASSISTANT.name)
            SmartHomeBackend.HOME_ASSISTANT else SmartHomeBackend.IOBROKER
    )
    val smartHomeBackend: StateFlow<SmartHomeBackend> = _smartHomeBackend

    fun setSmartHomeBackend(backend: SmartHomeBackend) {
        prefs.edit().putString(KEY_SMART_HOME_BACKEND, backend.name).apply()
        _smartHomeBackend.value = backend
    }

    private val _homeAssistantConfig = MutableStateFlow(loadHomeAssistantConfig())
    val homeAssistantConfig: StateFlow<HomeAssistantConfig?> = _homeAssistantConfig

    private fun loadHomeAssistantConfig(): HomeAssistantConfig? {
        val host = prefs.getString(KEY_HA_HOST, null) ?: return null
        return HomeAssistantConfig(host, prefs.getInt(KEY_HA_PORT, 8123), prefs.getBoolean(KEY_HA_USE_HTTPS, false))
    }

    fun setHomeAssistantConfig(config: HomeAssistantConfig) {
        prefs.edit()
            .putString(KEY_HA_HOST, config.host)
            .putInt(KEY_HA_PORT, config.port)
            .putBoolean(KEY_HA_USE_HTTPS, config.useHttps)
            .apply()
        _homeAssistantConfig.value = config
    }

    fun clearHomeAssistantConfig() {
        prefs.edit().remove(KEY_HA_HOST).remove(KEY_HA_PORT).remove(KEY_HA_USE_HTTPS).apply()
        _homeAssistantConfig.value = null
    }
}
