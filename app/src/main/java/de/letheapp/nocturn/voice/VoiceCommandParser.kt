package de.letheapp.nocturn.voice

/** Per [VoiceCommandParser] aus einem gesprochenen deutschen Satz erkannter Befehl. */
sealed interface VoiceCommand {
    data class StartTimer(val durationMillis: Long) : VoiceCommand
    data object StartStopwatch : VoiceCommand
    data object PauseStopwatch : VoiceCommand
    data object ResetStopwatch : VoiceCommand
    data class CreateAlarm(val hour: Int, val minute: Int) : VoiceCommand
    data class Unrecognized(val rawText: String) : VoiceCommand
}

/**
 * Rein lokales, deterministisches Regel-/Regex-Parsing für Sprachbefehle (Timer, Stoppuhr,
 * einmaliger Wecker) — bewusst ohne LLM/Server-Anfrage, passend zur FOSS-/Privatsphäre-
 * Leitlinie des Projekts (Sprachdaten verlassen über den System-Spracherkenner hinaus nie
 * das Gerät). Der eigentliche Sprache-zu-Text-Schritt läuft über Androids Board-Mittel
 * ([android.speech.RecognizerIntent]) — hier wird nur der bereits transkribierte Text
 * ausgewertet.
 */
object VoiceCommandParser {

    private val ALARM_TIME_REGEX =
        Regex("um\\s+(\\d{1,2})(?:\\s*[:.]\\s*(\\d{2})|\\s*uhr\\s*(\\d{1,2})|\\s+uhr)?")
    private val HOURS_REGEX = Regex("(\\d+)\\s*stunden?")
    private val MINUTES_REGEX = Regex("(\\d+)\\s*min(?:ute[n]?)?")
    private val SECONDS_REGEX = Regex("(\\d+)\\s*sekunden?")

    fun parse(text: String): VoiceCommand {
        val normalized = text.trim().lowercase()
        parseAlarm(normalized)?.let { return it }
        parseTimer(normalized)?.let { return it }
        parseStopwatch(normalized)?.let { return it }
        return VoiceCommand.Unrecognized(text.trim())
    }

    private fun parseAlarm(text: String): VoiceCommand? {
        if (!(text.contains("wecke") || text.contains("wecker") || text.contains("alarm"))) return null
        val match = ALARM_TIME_REGEX.find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].ifBlank { match.groupValues[3] }.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return VoiceCommand.CreateAlarm(hour, minute)
    }

    private fun parseTimer(text: String): VoiceCommand? {
        if (!text.contains("timer")) return null
        var totalMillis = 0L
        HOURS_REGEX.find(text)?.let { totalMillis += it.groupValues[1].toLong() * 3_600_000L }
        MINUTES_REGEX.find(text)?.let { totalMillis += it.groupValues[1].toLong() * 60_000L }
        SECONDS_REGEX.find(text)?.let { totalMillis += it.groupValues[1].toLong() * 1_000L }
        if (totalMillis <= 0L) return null
        return VoiceCommand.StartTimer(totalMillis)
    }

    private fun parseStopwatch(text: String): VoiceCommand? {
        if (!text.contains("stoppuhr")) return null
        return when {
            Regex("stopp(e|en)?\\b|halte|anhalten|pausier").containsMatchIn(text) -> VoiceCommand.PauseStopwatch
            Regex("zurücksetz|reset|löschen").containsMatchIn(text) -> VoiceCommand.ResetStopwatch
            else -> VoiceCommand.StartStopwatch
        }
    }
}
