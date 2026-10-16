package de.letheapp.nocturn.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NocTurnWatchApp()
        }
    }
}

@Composable
private fun NocTurnWatchApp() {
    val context = LocalContext.current
    val repository = remember { WearAgendaRepository(context) }
    var agenda by remember { mutableStateOf<WatchAgenda?>(null) }

    LaunchedEffect(Unit) {
        agenda = repository.loadCurrent()
        repository.observeAgenda().onEach { agenda = it }.collect {}
    }

    MaterialTheme {
        AgendaScreen(agenda)
    }
}

@Composable
private fun AgendaScreen(agenda: WatchAgenda?) {
    val entries = agenda?.entries.orEmpty()

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Keine Einträge.\nIn NocTurn unter\n\"WearOS-Verwaltung\"\nQuellen aktivieren.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2
            )
        }
        return
    }

    val byDay = entries.groupBy { it.epochDay }.toSortedMap()
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.") }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        byDay.forEach { (epochDay, dayEntries) ->
            item {
                val date = LocalDate.ofEpochDay(epochDay)
                val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                Text(
                    "$weekday, ${date.format(dayFormatter)}",
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(dayEntries) { entry ->
                AgendaEntryRow(entry)
            }
        }
    }
}

@Composable
private fun AgendaEntryRow(entry: WatchAgendaEntry) {
    Row(modifier = Modifier.fillMaxSize().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(8.dp)
                .background(Color(entry.colorArgb), CircleShape)
        )
        val prefix = entry.timeLabel?.let { "$it  " }.orEmpty()
        Text(prefix + entry.label, style = MaterialTheme.typography.body2, maxLines = 2)
    }
}
