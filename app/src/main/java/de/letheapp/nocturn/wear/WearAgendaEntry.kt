package de.letheapp.nocturn.wear

/** Welche Art von Eintrag ein [WearAgendaEntry] auf der Smartwatch darstellt. */
enum class WearAgendaEntryType { SHIFT, BIRTHDAY, PUBLIC_HOLIDAY, SCHOOL_HOLIDAY }

/**
 * Ein für die Smartwatch aufbereiteter Kalendereintrag — bewusst eine eigene, schlanke
 * Datenklasse statt [de.letheapp.nocturn.data.repository.AgendaEntry] wiederzuverwenden: sie muss
 * sich verlustfrei nach JSON serialisieren lassen (siehe [WearSyncManager]) und wird unabhängig
 * von der Handy-Anzeige nach den WearOS-Verwaltung-Schaltern ([WearVisibleSourceRepository])
 * gefiltert, nicht nach den Handy-Kalender-Einstellungen.
 */
data class WearAgendaEntry(
    val epochDay: Long,
    val label: String,
    val colorArgb: Int,
    val timeLabel: String?,
    val type: WearAgendaEntryType
)
