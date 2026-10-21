package de.letheapp.nocturn.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.letheapp.nocturn.data.db.AppointmentEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Package-Name von VectoMail (siehe `/opt/Email`-Projekt, Fork von Thunderbird for Android/K-9). */
private const val VECTOMAIL_PACKAGE = "de.letheapp.vectomail"

/**
 * VectoMail hat noch keine eigene Download-/Landingpage (das Email-Projekt ist noch in der
 * Planungs-/frühen Build-Phase) — bis dahin verlinkt der E-Mail-Erinnerungs-Footer bewusst auf die
 * echte, bestehende letheapp.de-Hauptseite statt eine noch nicht existierende VectoMail-Unterseite
 * oder ein noch nicht vorhandenes Logo-Bild zu erfinden. Sobald VectoMail eine eigene Seite/ein
 * eigenes Logo-Asset hat, hier einfach die URL ersetzen.
 */
private const val VECTOMAIL_INFO_URL = "https://letheapp.de"

/**
 * Baut die E-Mail-Erinnerung über Android-Boardmittel (Senden-Intent) statt eigener SMTP-
 * Zugangsdaten in NocTurn — passend zur Vorgabe, dafür Android-Standardmechanismen zu nutzen.
 * Ist VectoMail installiert, wird die E-Mail-App-Auswahl übersprungen und direkt dorthin
 * adressiert (echte "direkte VectoMail-Verbindung" für den Versand); ohne VectoMail öffnet sich
 * die normale Android-Auswahl für installierte Mail-Apps.
 */
object EmailReminderSender {

    fun isVectoMailInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(VECTOMAIL_PACKAGE, 0) }.isSuccess

    fun buildComposeIntent(context: Context, appointment: AppointmentEntity, recipientEmail: String): Intent {
        val subject = "Erinnerung: ${appointment.title}"
        val body = buildBody(appointment)

        val vectoMailInstalled = isVectoMailInstalled(context)
        return if (vectoMailInstalled) {
            Intent(Intent.ACTION_SEND).apply {
                setPackage(VECTOMAIL_PACKAGE)
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun buildBody(appointment: AppointmentEntity): String {
        val time = Instant.ofEpochMilli(appointment.startEpochMillis).atZone(ZoneId.systemDefault())
        val timeText = time.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy HH:mm"))
        return buildString {
            appendLine("Termin: ${appointment.title}")
            appendLine("Zeit: $timeText")
            if (appointment.location.isNotBlank()) appendLine("Ort: ${appointment.location}")
            if (appointment.description.isNotBlank()) {
                appendLine()
                appendLine(appointment.description)
            }
            appendLine()
            appendLine("---")
            appendLine("Gesendet über NocTurn")
            appendLine("E-Mails verwalten mit VectoMail: $VECTOMAIL_INFO_URL")
        }
    }
}
