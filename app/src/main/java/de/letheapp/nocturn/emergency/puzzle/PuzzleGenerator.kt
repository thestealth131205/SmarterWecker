package de.letheapp.nocturn.emergency.puzzle

import kotlin.random.Random

private enum class PuzzleKind { SHAPES, EMOJIS, NUMBERS }

/**
 * Erzeugt bei jedem Aufruf eine neue, zufällige Zuordnungsaufgabe: Elementtyp
 * (Geometrien/Emojis/Zahlen), Farbe/Form/Zahl, Sequenzlänge und Pool-Zusammensetzung variieren
 * jedes Mal, damit sich die Lösung nicht einprägen lässt.
 */
object PuzzleGenerator {

    private const val MIN_SEQUENCE_LENGTH = 4
    private const val MAX_SEQUENCE_LENGTH = 6
    private const val DISTRACTOR_COUNT = 4
    private const val MAX_TWO_DIGIT_NUMBER = 99

    fun generate(random: Random = Random): Puzzle {
        val sequenceLength = random.nextInt(MIN_SEQUENCE_LENGTH, MAX_SEQUENCE_LENGTH + 1)
        return when (PuzzleKind.entries.random(random)) {
            PuzzleKind.SHAPES -> generateShapePuzzle(sequenceLength, random)
            PuzzleKind.EMOJIS -> generateEmojiPuzzle(sequenceLength, random)
            PuzzleKind.NUMBERS -> generateNumberPuzzle(sequenceLength, random)
        }
    }

    /**
     * Pro Durchgang bekommt jede Sequenz-Position eine andere Farbe (keine Wiederholung
     * innerhalb eines Puzzles) und eine zufällige Form — da [PuzzleElement.ShapeElement.matches]
     * nur auf die Farbe prüft, ist die Form rein kosmetisch und beliebig austauschbar. Die
     * Ablenker im Pool bekommen bewusst Farben, die in der Sequenz nicht vorkommen, damit sie
     * nie versehentlich einen Zielplatz erfüllen.
     */
    private fun generateShapePuzzle(sequenceLength: Int, random: Random): Puzzle {
        val shuffledColors = PUZZLE_COLORS.shuffled(random)
        val sequenceColors = shuffledColors.take(sequenceLength)
        val distractorColors = shuffledColors.drop(sequenceLength).take(DISTRACTOR_COUNT)

        val sequence = sequenceColors.map { color ->
            PuzzleElement.ShapeElement(PuzzleShape.entries.random(random), color)
        }
        val distractors = distractorColors.map { color ->
            PuzzleElement.ShapeElement(PuzzleShape.entries.random(random), color)
        }
        return Puzzle(sequence = sequence, pool = (sequence + distractors).shuffled(random))
    }

    private fun generateEmojiPuzzle(sequenceLength: Int, random: Random): Puzzle {
        val chosen = PUZZLE_EMOJIS.shuffled(random).take((sequenceLength + DISTRACTOR_COUNT).coerceAtMost(PUZZLE_EMOJIS.size))
        val sequence = chosen.take(sequenceLength).map { PuzzleElement.EmojiElement(it) }
        val pool = chosen.map { PuzzleElement.EmojiElement(it) }.shuffled(random)
        return Puzzle(sequence = sequence, pool = pool)
    }

    /** Max. zweistellige, farbige Zahlen; die Zahl selbst unterscheidet Ablenker eindeutig von der Sequenz. */
    private fun generateNumberPuzzle(sequenceLength: Int, random: Random): Puzzle {
        val totalCount = sequenceLength + DISTRACTOR_COUNT
        val numbers = (0..MAX_TWO_DIGIT_NUMBER).shuffled(random).take(totalCount)
        val elements = numbers.map { number -> PuzzleElement.NumberElement(number, PUZZLE_COLORS.random(random)) }
        val sequence = elements.take(sequenceLength)
        val pool = elements.shuffled(random)
        return Puzzle(sequence = sequence, pool = pool)
    }
}
