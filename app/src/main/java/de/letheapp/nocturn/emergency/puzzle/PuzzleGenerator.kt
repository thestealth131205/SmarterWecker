package de.letheapp.nocturn.emergency.puzzle

import kotlin.random.Random

/**
 * Erzeugt bei jedem Aufruf eine neue, zufällige Zuordnungsaufgabe: Elementtyp
 * (Geometrien/Emojis), Farbe/Form, Sequenzlänge und Pool-Zusammensetzung variieren jedes Mal,
 * damit sich die Lösung nicht einprägen lässt.
 */
object PuzzleGenerator {

    private const val MIN_SEQUENCE_LENGTH = 4
    private const val MAX_SEQUENCE_LENGTH = 6
    private const val DISTRACTOR_COUNT = 4

    fun generate(random: Random = Random): Puzzle {
        val useShapes = random.nextBoolean()
        val sequenceLength = random.nextInt(MIN_SEQUENCE_LENGTH, MAX_SEQUENCE_LENGTH + 1)

        val allCandidates: List<PuzzleElement> = if (useShapes) {
            allShapeColorCombinations().shuffled(random)
        } else {
            PUZZLE_EMOJIS.map { PuzzleElement.EmojiElement(it) }.shuffled(random)
        }

        val poolSize = (sequenceLength + DISTRACTOR_COUNT).coerceAtMost(allCandidates.size)
        val chosen = allCandidates.take(poolSize)
        val sequence = chosen.take(sequenceLength)
        val pool = chosen.shuffled(random)

        return Puzzle(sequence = sequence, pool = pool)
    }

    private fun allShapeColorCombinations(): List<PuzzleElement.ShapeElement> =
        PuzzleShape.entries.flatMap { shape -> PUZZLE_COLORS.map { color -> PuzzleElement.ShapeElement(shape, color) } }
}
