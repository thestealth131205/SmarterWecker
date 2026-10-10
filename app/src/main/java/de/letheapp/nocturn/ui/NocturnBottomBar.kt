package de.letheapp.nocturn.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Die drei gleichrangigen Hauptbereiche der App, erreichbar über die untere Tab-Leiste. */
enum class NocturnTab {
    WECKER,
    WETTER,
    KALENDER
}

/**
 * Untere Tab-Leiste (Wecker/Wetter/Kalender) nach dem Vorbild gängiger Schichtplan-Apps –
 * macht die drei Hauptbereiche jederzeit mit einem Tap erreichbar, statt sie nur über
 * Icons/Menüs in der jeweiligen Titelleiste zu verschachteln.
 */
@Composable
fun NocturnBottomBar(selected: NocturnTab, onSelect: (NocturnTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == NocturnTab.WECKER,
            onClick = { onSelect(NocturnTab.WECKER) },
            icon = { Icon(Icons.Filled.Alarm, contentDescription = null) },
            label = { Text("Wecker") }
        )
        NavigationBarItem(
            selected = selected == NocturnTab.WETTER,
            onClick = { onSelect(NocturnTab.WETTER) },
            icon = { Icon(Icons.Filled.WbSunny, contentDescription = null) },
            label = { Text("Wetter") }
        )
        NavigationBarItem(
            selected = selected == NocturnTab.KALENDER,
            onClick = { onSelect(NocturnTab.KALENDER) },
            icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
            label = { Text("Kalender") }
        )
    }
}
