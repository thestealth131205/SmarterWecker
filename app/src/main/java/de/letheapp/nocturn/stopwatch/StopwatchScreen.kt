package de.letheapp.nocturn.stopwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    onBack: () -> Unit,
    viewModel: StopwatchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            nowMillis = System.currentTimeMillis()
            delay(50)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stoppuhr") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = formatElapsed(state.elapsedMillisNow(nowMillis)),
                fontSize = 56.sp,
                style = MaterialTheme.typography.displayMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.isRunning) {
                    OutlinedButton(onClick = viewModel::lap) { Text("Runde") }
                    FilledTonalButton(onClick = viewModel::pause) { Text("Stopp") }
                } else {
                    if (state.elapsedMillisNow(nowMillis) > 0L) {
                        OutlinedButton(onClick = viewModel::reset) { Text("Zurücksetzen") }
                    }
                    Button(onClick = viewModel::start) { Text("Start") }
                }
            }

            if (state.laps.isNotEmpty()) {
                HorizontalDivider()
                // Modifier.weight(1f) statt einer einfachen Box: die umgebende Column ist über
                // fillMaxSize() bereits höhenbeschränkt, eine LazyColumn ohne eigene Höhenvorgabe
                // würde darin sonst mit einer unbegrenzten Höhenvorgabe abstürzen.
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(state.laps.withIndex().toList().reversed()) { (index, lapMillis) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Runde ${index + 1}")
                            Text(formatElapsed(lapMillis))
                        }
                    }
                }
            }
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val totalMillis = millis.coerceAtLeast(0L)
    val hours = totalMillis / 3_600_000L
    val minutes = (totalMillis % 3_600_000L) / 60_000L
    val seconds = (totalMillis % 60_000L) / 1000L
    val hundredths = (totalMillis % 1000L) / 10L
    return if (hours > 0) {
        "%02d:%02d:%02d,%02d".format(hours, minutes, seconds, hundredths)
    } else {
        "%02d:%02d,%02d".format(minutes, seconds, hundredths)
    }
}
