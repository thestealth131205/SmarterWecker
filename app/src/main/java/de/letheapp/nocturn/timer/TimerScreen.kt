package de.letheapp.nocturn.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

/** Presets für die Schnellauswahl, in Minuten. */
private val PRESET_MINUTES = listOf(1, 3, 5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    initialDurationMillis: Long = 0L,
    initialLabel: String = "",
    onBack: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isActive, state.isPaused, state.isRinging) {
        while (state.isActive && !state.isPaused) {
            nowMillis = System.currentTimeMillis()
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (state.isActive) {
                RunningTimerContent(
                    state = state,
                    nowMillis = nowMillis,
                    onPauseResume = viewModel::pauseOrResume,
                    onAddMinute = viewModel::addMinute,
                    onCancel = viewModel::cancel
                )
            } else {
                TimerSetupContent(
                    initialDurationMillis = initialDurationMillis,
                    initialLabel = initialLabel,
                    onStart = viewModel::start
                )
            }
        }
    }
}

@Composable
private fun RunningTimerContent(
    state: TimerState,
    nowMillis: Long,
    onPauseResume: () -> Unit,
    onAddMinute: () -> Unit,
    onCancel: () -> Unit
) {
    val remaining = state.remainingMillisNow(nowMillis)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.label.isNotBlank()) {
            Text(state.label, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = if (state.isRinging) "Abgelaufen" else TimerService.formatRemaining(remaining),
            fontSize = 56.sp,
            style = MaterialTheme.typography.displayMedium
        )
        if (state.isRinging) {
            Button(onClick = onCancel) { Text("Stop") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAddMinute) { Text("+1 Min.") }
                FilledTonalButton(onClick = onPauseResume) {
                    Text(if (state.isPaused) "Fortsetzen" else "Pause")
                }
            }
            Button(onClick = onCancel) { Text("Abbrechen") }
        }
    }
}

@Composable
private fun TimerSetupContent(
    initialDurationMillis: Long,
    initialLabel: String,
    onStart: (durationMillis: Long, label: String) -> Unit
) {
    val initialTotalSeconds = (initialDurationMillis / 1000L).toInt()
    var hours by remember { mutableStateOf((initialTotalSeconds / 3600).let { if (it > 0) it.toString() else "" }) }
    var minutes by remember { mutableStateOf(((initialTotalSeconds % 3600) / 60).let { if (it > 0) it.toString() else "" }) }
    var seconds by remember { mutableStateOf((initialTotalSeconds % 60).let { if (it > 0) it.toString() else "" }) }
    var label by remember { mutableStateOf(initialLabel) }

    val durationMillis = ((hours.toIntOrNull() ?: 0) * 3600L +
        (minutes.toIntOrNull() ?: 0) * 60L +
        (seconds.toIntOrNull() ?: 0)) * 1000L

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hours,
                onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                label = { Text("Std.") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                label = { Text("Min.") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                label = { Text("Sek.") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESET_MINUTES.forEach { presetMinutes ->
                FilterChip(
                    selected = false,
                    onClick = {
                        hours = ""
                        minutes = presetMinutes.toString()
                        seconds = ""
                    },
                    label = { Text("$presetMinutes Min.") }
                )
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Bezeichnung (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = durationMillis > 0,
            onClick = { onStart(durationMillis, label.trim()) }
        ) {
            Text("Start")
        }
    }
}
