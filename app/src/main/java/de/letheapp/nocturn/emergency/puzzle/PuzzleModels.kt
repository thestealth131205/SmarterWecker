package de.letheapp.nocturn.emergency.puzzle

import androidx.compose.ui.graphics.Color

enum class PuzzleShape {
    CIRCLE, TRIANGLE, SQUARE, STAR, HEXAGON, DIAMOND
}

val PUZZLE_COLORS = listOf(
    Color(0xFF2196F3), // Blau
    Color(0xFFFFC107), // Gelb
    Color(0xFFE91E63), // Pink
    Color(0xFF4CAF50), // Grün
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0)  // Lila
)

val PUZZLE_EMOJIS = listOf("😀", "😎", "😡", "😭", "😴", "🤢", "🥳", "😱", "🤔", "😇")

/**
 * Ein einzelnes Element der Zuordnungsaufgabe: entweder eine farbige Geometrie oder ein Emoji.
 * [key] identifiziert das Element eindeutig für Vergleiche (Shape+Farbe bzw. der Emoji-String).
 */
sealed class PuzzleElement {
    abstract val key: String

    data class ShapeElement(val shape: PuzzleShape, val color: Color) : PuzzleElement() {
        override val key: String get() = "${shape.name}_${color.value}"
    }

    data class EmojiElement(val emoji: String) : PuzzleElement() {
        override val key: String get() = emoji
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
