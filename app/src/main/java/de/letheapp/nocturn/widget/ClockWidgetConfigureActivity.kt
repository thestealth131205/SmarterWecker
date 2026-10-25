package de.letheapp.nocturn.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.onboarding.AppPreferences
import de.letheapp.nocturn.ui.theme.NocTurnTheme
import de.letheapp.nocturn.weather.WeatherRepository
import javax.inject.Inject

/** Ausrichtung des Uhr-Widget-Inhalts innerhalb seiner eigenen Box - sinnvoll z. B. wenn das
 *  Widget direkt am linken bzw. rechten Bildschirmrand platziert wird. Android bietet keine API,
 *  um die tatsächliche Position eines Homescreen-Widgets auf dem Bildschirm zu ermitteln, daher
 *  wird die Ausrichtung beim Hinzufügen des Widgets manuell gewählt (jederzeit über das erneute
 *  Öffnen der Widget-Konfiguration änderbar, je nach Launcher z. B. per langem Gedrückthalten). */
enum class WidgetAlignment { LEFT, CENTER, RIGHT }

/**
 * Widget-Konfigurationsaktivität für das Uhr-Widget: fragt beim Hinzufügen immer die Ausrichtung
 * ab (Links/Mitte/Rechts, siehe [WidgetAlignment]) und zusätzlich, welcher Ort gezeigt werden
 * soll, falls der Nutzer mehrere Wetter-Orte angelegt hat (siehe `LocationListScreen`) — gibt es
 * nur den Hauptort, entfällt die Ortsauswahl automatisch.
 */
@AndroidEntryPoint
class ClockWidgetConfigureActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
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

        setContent {
            val dynamicColorEnabled by appPreferences.dynamicColorEnabled.collectAsState()
            NocTurnTheme(dynamicColor = dynamicColorEnabled) {
                val manualLocation by appPreferences.manualLocation.collectAsState()
                val entries = listOf(WeatherRepository.MAIN_LOCATION_ID to (manualLocation?.name ?: "Automatisch")) +
                    additional.map { it.id to it.name }
                var selectedLocationId by remember { mutableStateOf(WeatherRepository.MAIN_LOCATION_ID) }
                var selectedAlignment by remember { mutableStateOf(WidgetAlignment.CENTER) }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Widget einrichten") }) }
                ) { padding ->
                    Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
                        Text(
                            "Ausrichtung",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        )
                        Text(
                            "Links, wenn das Widget am linken Bildschirmrand liegt, rechts bei einer Platzierung am rechten Rand.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            WidgetAlignment.entries.forEachIndexed { index, alignment ->
                                SegmentedButton(
                                    selected = selectedAlignment == alignment,
                                    onClick = { selectedAlignment = alignment },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = WidgetAlignment.entries.size)
                                ) {
                                    Text(
                                        when (alignment) {
                                            WidgetAlignment.LEFT -> "Links"
                                            WidgetAlignment.CENTER -> "Mitte"
                                            WidgetAlignment.RIGHT -> "Rechts"
                                        }
                                    )
                                }
                            }
                        }

                        if (additional.isNotEmpty()) {
                            Text(
                                "Ort",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(entries) { (id, name) ->
                                    ListItem(
                                        headlineContent = { Text(name) },
                                        leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                                        trailingContent = {
                                            if (id == selectedLocationId) {
                                                Text("✓", color = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().clickable { selectedLocationId = id }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { saveSelectionAndFinish(selectedLocationId, selectedAlignment) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Text("Fertig")
                        }
                    }
                }
            }
        }
    }

    private fun saveSelectionAndFinish(locationId: String, alignment: WidgetAlignment) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PREFIX + appWidgetId, locationId)
            .putString(ALIGNMENT_KEY_PREFIX + appWidgetId, alignment.name)
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
        private const val ALIGNMENT_KEY_PREFIX = "widget_alignment_"

        fun locationIdFor(context: Context, appWidgetId: Int): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + appWidgetId, WeatherRepository.MAIN_LOCATION_ID)
                ?: WeatherRepository.MAIN_LOCATION_ID

        /** Default [WidgetAlignment.CENTER], damit bereits platzierte Widgets ohne gespeicherte
         *  Auswahl (angelegt vor Einführung dieser Einstellung) ihr bisheriges Verhalten behalten. */
        fun alignmentFor(context: Context, appWidgetId: Int): WidgetAlignment {
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ALIGNMENT_KEY_PREFIX + appWidgetId, null)
            return stored?.let { runCatching { WidgetAlignment.valueOf(it) }.getOrNull() } ?: WidgetAlignment.CENTER
        }

        fun clearSelection(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_PREFIX + appWidgetId)
                .remove(ALIGNMENT_KEY_PREFIX + appWidgetId)
                .apply()
        }
    }
}
