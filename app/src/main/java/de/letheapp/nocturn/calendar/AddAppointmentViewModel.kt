package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.AppointmentPriority
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Vom Nutzer per Dateiauswahldialog gewählter, aber noch nicht gespeicherter Anhang. */
data class PendingAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String?
)

@HiltViewModel
class AddAppointmentViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    fun save(
        title: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        priority: AppointmentPriority = AppointmentPriority.NORMAL,
        reminderMinutesBefore: Int? = null,
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
                reminderMinutesBefore = reminderMinutesBefore,
                showAsBusy = showAsBusy
            )
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
