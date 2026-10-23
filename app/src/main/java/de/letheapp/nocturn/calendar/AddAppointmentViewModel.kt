package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.letheapp.nocturn.contacts.ContactEmailRepository
import de.letheapp.nocturn.contacts.ContactEmailSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.AppointmentPriority
import de.letheapp.nocturn.data.db.ReminderChannel
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Vom Nutzer per Dateiauswahldialog gewählter, aber noch nicht gespeicherter Anhang. */
data class PendingAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String?
)

/** Eine im Formular hinzugefügte, aber noch nicht gespeicherte Erinnerung. */
data class PendingReminder(
    val minutesBefore: Int,
    val channel: ReminderChannel = ReminderChannel.NOTIFICATION,
    val emailAddress: String? = null
)

/** Geladene Daten eines bestehenden Termins fürs Bearbeiten-Formular, siehe [AddAppointmentViewModel.loadForEdit]. */
data class AppointmentEditData(
    val appointment: AppointmentEntity,
    val reminders: List<PendingReminder>,
    val attachments: List<PendingAttachment>
)

@HiltViewModel
class AddAppointmentViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val contactEmailRepository: ContactEmailRepository
) : ViewModel() {

    suspend fun searchContactEmails(query: String): List<ContactEmailSuggestion> =
        contactEmailRepository.search(query)

    /** Lädt einen bestehenden Termin samt Erinnerungen/Anhängen für den Bearbeiten-Screen. */
    suspend fun loadForEdit(appointmentId: Long): AppointmentEditData? {
        val appointment = calendarRepository.getAppointment(appointmentId) ?: return null
        val reminders = calendarRepository.getAppointmentRemindersOnce(appointmentId).map {
            PendingReminder(it.minutesBefore, ReminderChannel.valueOf(it.channel), it.emailAddress)
        }
        val attachments = calendarRepository.getAppointmentAttachmentsOnce(appointmentId).map {
            PendingAttachment(it.uri, it.fileName, it.mimeType)
        }
        return AppointmentEditData(appointment, reminders, attachments)
    }

    /**
     * Speichert Änderungen an einem bestehenden Termin. Erinnerungen/Anhänge werden komplett
     * ersetzt (alte entfernt, aktuelle Formular-Liste neu angelegt) statt einzeln abgeglichen —
     * einfacher als ein Diff und für die überschaubare Anzahl pro Termin unkritisch.
     */
    fun update(
        appointmentId: Long,
        title: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        priority: AppointmentPriority = AppointmentPriority.NORMAL,
        reminders: List<PendingReminder> = emptyList(),
        showAsBusy: Boolean = true,
        attachments: List<PendingAttachment> = emptyList(),
        onSaved: () -> Unit
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            calendarRepository.updateAppointmentFull(
                id = appointmentId,
                title = title,
                location = location,
                description = description,
                startEpochMillis = startEpochMillis,
                endEpochMillis = maxOf(startEpochMillis, endEpochMillis),
                allDay = allDay,
                priority = priority,
                showAsBusy = showAsBusy
            )

            calendarRepository.getAppointmentRemindersOnce(appointmentId).forEach {
                calendarRepository.removeAppointmentReminder(it)
            }
            reminders.forEach { reminder ->
                calendarRepository.addAppointmentReminder(
                    appointmentId = appointmentId,
                    minutesBefore = reminder.minutesBefore,
                    channel = reminder.channel,
                    emailAddress = reminder.emailAddress
                )
            }

            calendarRepository.getAppointmentAttachmentsOnce(appointmentId).forEach {
                calendarRepository.removeAppointmentAttachment(it)
            }
            attachments.forEach { attachment ->
                calendarRepository.addAppointmentAttachment(
                    appointmentId = appointmentId,
                    uri = attachment.uri,
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType
                )
            }
            onSaved()
        }
    }

    fun delete(appointmentId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            calendarRepository.getAppointment(appointmentId)?.let { calendarRepository.deleteAppointment(it) }
            onDeleted()
        }
    }

    fun save(
        title: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        priority: AppointmentPriority = AppointmentPriority.NORMAL,
        reminders: List<PendingReminder> = emptyList(),
        showAsBusy: Boolean = true,
        attachments: List<PendingAttachment> = emptyList(),
        onSaved: () -> Unit
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val appointmentId = calendarRepository.addAppointment(
                title = title,
                location = location,
                description = description,
                startEpochMillis = startEpochMillis,
                endEpochMillis = maxOf(startEpochMillis, endEpochMillis),
                allDay = allDay,
                priority = priority,
                showAsBusy = showAsBusy
            )
            reminders.forEach { reminder ->
                calendarRepository.addAppointmentReminder(
                    appointmentId = appointmentId,
                    minutesBefore = reminder.minutesBefore,
                    channel = reminder.channel,
                    emailAddress = reminder.emailAddress
                )
            }
            attachments.forEach { attachment ->
                calendarRepository.addAppointmentAttachment(
                    appointmentId = appointmentId,
                    uri = attachment.uri,
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType
                )
            }
            onSaved()
        }
    }
}
