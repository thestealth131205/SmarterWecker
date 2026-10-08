package de.letheapp.nocturn.calendar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Feste Farbpalette zur Schichtauswahl. Eine freie Farbwahl (Farbrad) würde eine
 * zusätzliche Abhängigkeit erfordern; die kuratierte Palette deckt den Zweck
 * ("frei wählbare Farbe pro Schicht") ohne Extra-Library ab.
 */
val SHIFT_COLOR_PALETTE: List<Color> = listOf(
    Color(0xFFEF5350), // Rot
    Color(0xFFFF9800), // Orange
    Color(0xFFFFCA28), // Gelb
    Color(0xFF66BB6A), // Grün
    Color(0xFF26A69A), // Türkis
    Color(0xFF42A5F5), // Blau
    Color(0xFF5C6BC0), // Indigo
    Color(0xFFAB47BC), // Violett
    Color(0xFFEC407A), // Pink
    Color(0xFF8D6E63)  // Braun
)

val DEFAULT_SHIFT_COLOR_ARGB: Int = SHIFT_COLOR_PALETTE.first().toArgb()
