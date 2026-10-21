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
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.AppointmentReminderEntity
import de.letheapp.nocturn.data.db.ReminderChannel
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
 * Zeigt die Erinnerung für einen Termin, geplant über [AppointmentReminderScheduler]. Lädt
 * Erinnerung und Termin bei Auslösung frisch aus der DB, damit zwischenzeitliche Änderungen
 * (z. B. Titel) berücksichtigt werden; existieren sie nicht mehr (gelöscht), passiert nichts.
 * Bei einer E-Mail-Erinnerung öffnet die Benachrichtigung eine vorausgefüllte Mail-Compose-Ansicht
 * (Android-Boardmittel, ggf. direkt in VectoMail, siehe [EmailReminderSender]) — ein automatischer,
 * stiller Versand ohne Nutzerbestätigung ist bewusst nicht vorgesehen (keine in NocTurn
 * gespeicherten Mail-Zugangsdaten nötig/gewünscht).
 */
@AndroidEntryPoint
class AppointmentReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var appointmentDao: AppointmentDao

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = appointmentDao.getReminderById(reminderId) ?: return@launch
                val appointment = appointmentDao.getById(reminder.appointmentId) ?: return@launch
                when (ReminderChannel.valueOf(reminder.channel)) {
                    ReminderChannel.EMAIL -> showEmailNotification(context, reminder, appointment)
                    ReminderChannel.NOTIFICATION -> showNotification(context, reminder, appointment)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        reminder: AppointmentReminderEntity,
        appointment: AppointmentEntity
    ) {
        val notificationManager = ensureChannel(context)
        val timeText = formatTime(appointment.startEpochMillis)
        val contentText = if (appointment.location.isBlank()) timeText else "$timeText · ${appointment.location}"

        // Eigene Action-Zeichenkette, da der Request-Code hier von der Erinnerungs-ID abhängt und
        // bei kleinen IDs (1/2/3) sonst mit den Uhr-/Kalender-Widget-PendingIntents kollidieren
        // könnte - PendingIntent-Gleichheit prüft Action/Component/Daten, nicht die Extras.
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_CALENDAR_FROM_REMINDER
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_CALENDAR, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_APPOINTMENT_REMINDERS)
            .setContentTitle(appointment.title.ifBlank { "Termin" })
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + reminder.id.toInt(), notification)
    }

    private fun showEmailNotification(
        context: Context,
        reminder: AppointmentReminderEntity,
        appointment: AppointmentEntity
    ) {
        val recipient = reminder.emailAddress
        if (recipient.isNullOrBlank()) {
            showNotification(context, reminder, appointment)
            return
        }
        val notificationManager = ensureChannel(context)
        val timeText = formatTime(appointment.startEpochMillis)

        val composeIntent = EmailReminderSender.buildComposeIntent(context, appointment, recipient)
        val composePendingIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            composeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_APPOINTMENT_REMINDERS)
            .setContentTitle(appointment.title.ifBlank { "Termin" })
            .setContentText("$timeText · E-Mail an $recipient")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(composePendingIntent)
            .addAction(0, "E-Mail senden", composePendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + reminder.id.toInt(), notification)
    }

    private fun ensureChannel(context: Context): NotificationManager {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_APPOINTMENT_REMINDERS, "Termin-Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT)
        )
        return notificationManager
    }

    private fun formatTime(epochMillis: Long): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return time.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }
}
