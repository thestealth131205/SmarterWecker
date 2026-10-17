package de.letheapp.nocturn.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.EXTRA_OPEN_CALENDAR
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.data.db.AppointmentDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

const val CHANNEL_APPOINTMENT_REMINDERS = "appointment_reminders"
private const val NOTIFICATION_ID_BASE = 3000
private const val ACTION_OPEN_CALENDAR_FROM_REMINDER = "de.letheapp.nocturn.calendar.ACTION_REMINDER_OPEN_CALENDAR"

/**
 * Zeigt die Erinnerungs-Benachrichtigung für einen Termin, geplant über
 * [AppointmentReminderScheduler]. Lädt den Termin bei Auslösung frisch aus der DB, damit
 * zwischenzeitliche Änderungen (z. B. Titel) berücksichtigt werden; existiert er nicht mehr
 * (gelöscht), passiert nichts.
 */
@AndroidEntryPoint
class AppointmentReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var appointmentDao: AppointmentDao

    override fun onReceive(context: Context, intent: Intent) {
        val appointmentId = intent.getLongExtra(EXTRA_APPOINTMENT_ID, -1L)
        if (appointmentId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appointment = appointmentDao.getById(appointmentId) ?: return@launch
                showNotification(context, appointmentId, appointment.title, appointment.location, appointment.startEpochMillis)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        appointmentId: Long,
        title: String,
        location: String,
        startEpochMillis: Long
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_APPOINTMENT_REMINDERS, "Termin-Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val time = Instant.ofEpochMilli(startEpochMillis).atZone(ZoneId.systemDefault())
        val timeText = time.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        val contentText = if (location.isBlank()) timeText else "$timeText · $location"

        // Eigene Action-Zeichenkette, da der Request-Code hier von der Termin-ID abhängt und bei
        // kleinen IDs (1/2/3) sonst mit den Uhr-/Kalender-Widget-PendingIntents kollidieren könnte
        // - PendingIntent-Gleichheit prüft Action/Component/Daten, nicht die Extras.
        val contentIntent = PendingIntent.getActivity(
            context,
            appointmentId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_CALENDAR_FROM_REMINDER
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_CALENDAR, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_APPOINTMENT_REMINDERS)
            .setContentTitle(title.ifBlank { "Termin" })
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + appointmentId.toInt(), notification)
    }
}
