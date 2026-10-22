package de.letheapp.nocturn.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.onboarding.AppPreferences
import de.letheapp.nocturn.ui.theme.NocTurnTheme
import de.letheapp.nocturn.weather.WeatherRepository
import javax.inject.Inject

/**
 * Widget-Konfigurationsaktivität für das Uhr-Widget: Hat der Nutzer mehrere Wetter-Orte angelegt
 * (siehe `LocationListScreen`), fragt sie beim Hinzufügen des Widgets, welchen Ort es anzeigen
 * soll. Gibt es nur den Hauptort, wird dieser ohne sichtbare UI sofort übernommen — Android
 * startet eine deklarierte `android:configure`-Activity für JEDES Widget-Hinzufügen, auch wenn
 * (noch) keine Auswahl nötig ist, daher der sofortige, unsichtbare Abschluss in diesem Fall.
 */
@AndroidEntryPoint
class ClockWidgetConfigureActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val additional = appPreferences.additionalLocations.value
        if (additional.isEmpty()) {
            saveSelectionAndFinish(WeatherRepository.MAIN_LOCATION_ID)
            return
        }

        setContent {
            val dynamicColorEnabled by appPreferences.dynamicColorEnabled.collectAsState()
            NocTurnTheme(dynamicColor = dynamicColorEnabled) {
                val manualLocation by appPreferences.manualLocation.collectAsState()
                val entries = listOf(WeatherRepository.MAIN_LOCATION_ID to (manualLocation?.name ?: "Automatisch")) +
                    additional.map { it.id to it.name }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Ort für dieses Widget") }) }
                ) { padding ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
                        items(entries) { (id, name) ->
                            ListItem(
                                headlineContent = { Text(name) },
                                leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().clickable { saveSelectionAndFinish(id) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveSelectionAndFinish(locationId: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PREFIX + appWidgetId, locationId)
            .apply()

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        // Das frisch platzierte Widget bekommt seinen ersten Refresh selbst erst über den
        // regulären 20-Minuten-Datentakt - sofortige Aktualisierung über den Standard-Weg, mit
        // dem AppWidgetProvider.onReceive ohnehin schon umgeht (ruft intern onUpdate() auf).
        sendBroadcast(
            Intent(this, ClockWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
        )
        finish()
    }

    companion object {
        private const val PREFS_NAME = "clock_widget_locations"
        private const val KEY_PREFIX = "widget_location_"

        fun locationIdFor(context: Context, appWidgetId: Int): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + appWidgetId, WeatherRepository.MAIN_LOCATION_ID)
                ?: WeatherRepository.MAIN_LOCATION_ID

        fun clearSelection(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_PREFIX + appWidgetId)
                .apply()
        }
    }
}
