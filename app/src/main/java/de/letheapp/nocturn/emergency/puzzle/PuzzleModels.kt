package de.letheapp.nocturn.emergency.puzzle

import androidx.compose.ui.graphics.Color

enum class PuzzleShape {
    CIRCLE, TRIANGLE, SQUARE, STAR, HEXAGON, DIAMOND
}

/**
 * Bewusst viele, gut unterscheidbare Farbtöne (siehe CLAUDE.md Kernfunktion 4): verhindert,
 * dass zwei Elemente im selben Durchgang zufällig ähnlich aussehen, wenn ein farbiger
 * Notfall-Hintergrund über dem Puzzle liegt.
 */
val PUZZLE_COLORS = listOf(
    Color(0xFF2196F3), // Blau
    Color(0xFFFFC107), // Gelb
    Color(0xFFE91E63), // Pink
    Color(0xFF4CAF50), // Grün
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0), // Lila
    Color(0xFF00BCD4), // Türkis
    Color(0xFFF44336), // Rot
    Color(0xFF3F51B5), // Indigo
    Color(0xFFCDDC39)  // Limette
)

val PUZZLE_EMOJIS = listOf("😀", "😎", "😡", "😭", "😴", "🤢", "🥳", "😱", "🤔", "😇")

/**
 * Ein einzelnes Element der Zuordnungsaufgabe: eine farbige Geometrie, ein Emoji oder eine
 * farbige Zahl. [key] identifiziert die konkrete Instanz eindeutig (für die Pool-Verwaltung
 * beim Drag & Drop), [matches] entscheidet, ob dieses Element einen Zielplatz erfüllt.
 *
 * Bei Geometrien zählt für [matches] bewusst nur die Farbe, nicht die Form: zwei gleichfarbige,
 * aber unterschiedlich geformte Elemente sollen austauschbar sein, damit man im Halbschlaf nicht
 * zusätzlich noch exakt die Form unterscheiden muss — nur die Farbe muss passen.
 */
sealed class PuzzleElement {
    abstract val key: String
    abstract fun matches(required: PuzzleElement): Boolean

    data class ShapeElement(val shape: PuzzleShape, val color: Color) : PuzzleElement() {
        override val key: String get() = "SHAPE_${shape.name}_${color.value}"
        override fun matches(required: PuzzleElement): Boolean =
            required is ShapeElement && required.color == color
    }

    data class EmojiElement(val emoji: String) : PuzzleElement() {
        override val key: String get() = "EMOJI_$emoji"
        override fun matches(required: PuzzleElement): Boolean =
            required is EmojiElement && required.emoji == emoji
    }

    /** Max. zweistellige, farbige Zahl als dritte Aufgaben-Variante neben Geometrien/Emojis. */
    data class NumberElement(val number: Int, val color: Color) : PuzzleElement() {
        override val key: String get() = "NUMBER_${number}_${color.value}"
        override fun matches(required: PuzzleElement): Boolean =
            required is NumberElement && required.number == number && required.color == color
    }
}

/**
 * Eine Puzzle-Instanz: [sequence] ist die oben vorgegebene Reihenfolge, [pool] enthält alle
 * Elemente (inkl. Ablenker) in zufälliger Anordnung, aus denen per Drag & Drop die passenden
 * Elemente an die richtige Stelle in [sequence] gezogen werden müssen.
 */
data class Puzzle(
    val sequence: List<PuzzleElement>,
    val pool: List<PuzzleElement>
)
