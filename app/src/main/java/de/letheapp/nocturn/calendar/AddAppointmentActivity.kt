package de.letheapp.nocturn.calendar

import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.ui.theme.NocTurnTheme

/**
 * Empfängt den Standard-Android-"Zum Kalender hinzufügen"-Intent (z. B. "Termin speichern"-
 * Button in einer Kinoticket- oder Buchungs-App) und bietet NocTurn so im System-Auswahlmenü
 * als Kalender-App an (siehe CLAUDE.md Kernfunktion 6, Intent-Filter im Manifest). Die Fremd-
 * App füllt Titel/Ort/Zeit über [CalendarContract]-Extras vor, der Nutzer bestätigt/korrigiert
 * sie hier, bevor der Termin als eigener [de.letheapp.nocturn.data.db.AppointmentEntity]
 * gespeichert wird.
 */
@AndroidEntryPoint
class AddAppointmentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty()
        val location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION).orEmpty()
        val description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty()
        val allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        val now = System.currentTimeMillis()
        val beginMillis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, now)
        val rawEndMillis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMillis)
        val endMillis = if (rawEndMillis > beginMillis) rawEndMillis else beginMillis

        setContent {
            NocTurnTheme {
                AddAppointmentScreen(
                    initialTitle = title,
                    initialLocation = location,
                    initialDescription = description,
                    initialBeginMillis = beginMillis,
                    initialEndMillis = endMillis,
                    initialAllDay = allDay,
                    onSaved = { finish() },
                    onCancelled = { finish() }
                )
            }
        }
    }
}
