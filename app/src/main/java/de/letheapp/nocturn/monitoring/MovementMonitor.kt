package de.letheapp.nocturn.monitoring

/**
 * Prüft, ob der Nutzer tatsächlich aufgestanden ist und sich fortbewegt hat
 * (reines Wegdrücken des Alarms reicht nicht). Ruft [onConfirmed] genau einmal auf,
 * sobald genug Bewegung erkannt wurde.
 */
interface MovementMonitor {
    fun start(onConfirmed: () -> Unit)
    fun stop()
}
