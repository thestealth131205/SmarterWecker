package de.letheapp.nocturn.calendar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Feste Farbpalette zur Schichtauswahl. Eine freie Farbwahl (Farbrad) würde eine
 * zusätzliche Abhängigkeit erfordern; die kuratierte Palette deckt den Zweck
 * ("frei wählbare Farbe pro Schicht") ohne Extra-Library ab. Bewusst pastellige
 * ("200"-Tonstufe) statt kräftig-gesättigter Töne, passend zur Farbgebung von Terminen/
 * Ereignissen (siehe [de.letheapp.nocturn.data.repository.CalendarRepository]).
 */
val SHIFT_COLOR_PALETTE: List<Color> = listOf(
    Color(0xFFEF9A9A), // Rot
    Color(0xFFFFCC80), // Orange
    Color(0xFFFFE082), // Gelb
    Color(0xFFA5D6A7), // Grün
    Color(0xFF80CBC4), // Türkis
    Color(0xFF90CAF9), // Blau
    Color(0xFF9FA8DA), // Indigo
    Color(0xFFCE93D8), // Violett
    Color(0xFFF48FB1), // Pink
    Color(0xFFBCAAA4)  // Braun
)

val DEFAULT_SHIFT_COLOR_ARGB: Int = SHIFT_COLOR_PALETTE.first().toArgb()
