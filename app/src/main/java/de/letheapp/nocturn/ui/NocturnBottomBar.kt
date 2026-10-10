package de.letheapp.nocturn.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Die drei gleichrangigen Hauptbereiche der App, erreichbar über die untere Tab-Leiste, in
 *  dieser festen Reihenfolge – wird auch für den Swipe-Wechsel auf der Tab-Leiste genutzt. */
enum class NocturnTab {
    WECKER,
    WETTER,
    KALENDER
}

private val TAB_ORDER = listOf(NocturnTab.WECKER, NocturnTab.WETTER, NocturnTab.KALENDER)
private const val BOTTOM_BAR_SWIPE_THRESHOLD_PX = 80f

/**
 * Untere Tab-Leiste (Wecker/Wetter/Kalender) nach dem Vorbild gängiger Schichtplan-Apps –
 * macht die drei Hauptbereiche jederzeit mit einem Tap erreichbar, statt sie nur über
 * Icons/Menüs in der jeweiligen Titelleiste zu verschachteln. Zusätzlich per Wischen auf der
 * Leiste selbst zwischen den Tabs wechselbar — bewusst nur hier, damit Wischen im jeweiligen
 * Bildschirminhalt (z. B. Monatswechsel im Kalender) ungestört bleibt.
 */
@Composable
fun NocturnBottomBar(selected: NocturnTab, onSelect: (NocturnTab) -> Unit) {
    var dragAccumulated by remember { mutableStateOf(0f) }
    NavigationBar(
        modifier = Modifier.pointerInput(selected) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val currentIndex = TAB_ORDER.indexOf(selected)
                    val targetIndex = when {
                        dragAccumulated <= -BOTTOM_BAR_SWIPE_THRESHOLD_PX -> currentIndex + 1
                        dragAccumulated >= BOTTOM_BAR_SWIPE_THRESHOLD_PX -> currentIndex - 1
                        else -> currentIndex
                    }
                    TAB_ORDER.getOrNull(targetIndex)?.let { if (it != selected) onSelect(it) }
                    dragAccumulated = 0f
                },
                onDragCancel = { dragAccumulated = 0f }
            ) { change, dragAmount ->
                change.consume()
                dragAccumulated += dragAmount
            }
        }
    ) {
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
