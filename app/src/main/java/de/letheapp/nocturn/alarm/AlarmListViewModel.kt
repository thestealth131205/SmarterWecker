package de.letheapp.nocturn.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.iobroker.IoBrokerDataPoint
import de.letheapp.nocturn.iobroker.IoBrokerRepository
import de.letheapp.nocturn.onboarding.AppPreferences
import de.letheapp.nocturn.voice.VoiceCommandExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Eine wählbare Verknüpfung für einen Alarm (job-übergreifend): entweder eine Rotation
 * (Schichtarbeit, [AlarmEntity.rotationId]) oder ein Job im Schüler/Student-Modus
 * (Wochenstundenplan, [AlarmEntity.timetableJobId]). Beide Felder sind nie gleichzeitig gesetzt.
 */
data class RotationOption(
    val label: String,
    val rotationId: Long? = null,
    val timetableJobId: Long? = null
)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val calendarRepository: CalendarRepository,
    private val ioBrokerRepository: IoBrokerRepository,
    private val shiftWakeupAlarmSyncer: ShiftWakeupAlarmSyncer,
    private val voiceCommandExecutor: VoiceCommandExecutor,
    private val appPreferences: AppPreferences
) : ViewModel() {

    init {
        // Fängt Fälle ab, in denen sich der Kalender geändert hat, ohne dass ein hier bekannter
        // Call-Pfad das selbst ausgelöst hat (z. B. Foto-Import, Nextcloud-Sync) — stellt sicher,
        // dass der "Nächste Schicht"-Alarm beim Öffnen des Wecker-Screens nie veraltet ist.
        viewModelScope.launch { shiftWakeupAlarmSyncer.sync() }
    }

    val alarms: StateFlow<List<AlarmEntity>> = alarmRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ioBrokerConnected: StateFlow<Boolean> = ioBrokerRepository.observeAccount()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _ioBrokerDataPoints = MutableStateFlow<List<IoBrokerDataPoint>>(emptyList())
    val ioBrokerDataPoints: StateFlow<List<IoBrokerDataPoint>> = _ioBrokerDataPoints

    /** Lädt die verfügbaren ioBroker-Datenpunkte neu (z. B. beim Öffnen des Lampen-Auswahl-Dialogs). */
    fun refreshIoBrokerDataPoints() {
        viewModelScope.launch {
            ioBrokerRepository.fetchDataPoints()
                .onSuccess { _ioBrokerDataPoints.value = it }
        }
    }

    val rotationOptions: StateFlow<List<RotationOption>> = combine(
        calendarRepository.observeAllRotations(),
        calendarRepository.observeJobs()
    ) { rotations, jobs ->
        val jobNameById = jobs.associateBy({ it.id }, { it.name })
        val rotationOptions = rotations.map { rotation ->
            RotationOption(
                label = "${rotation.name} · ${jobNameById[rotation.jobId].orEmpty()}",
                rotationId = rotation.id
            )
        }
        val timetableOptions = jobs.filter { it.mode == JobMode.STUDENT.name }.map { job ->
            RotationOption(label = "Stundenplan · ${job.name}", timetableJobId = job.id)
        }
        rotationOptions + timetableOptions
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Für die Anzeige des Schicht-Namens, falls ein Alarm auf [AlarmEntity.linkedShiftId] eingeengt ist. */
    val allShifts: StateFlow<List<ShiftEntity>> = calendarRepository.observeAllShifts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Die in einer Rotation tatsächlich verwendeten Schichten (ohne "Frei") — zur Auswahl, falls
     * ein Alarm statt an jede Schicht der Rotation an eine einzelne gekoppelt werden soll (siehe
     * [AlarmEntity.linkedShiftId]).
     */
    suspend fun rotationShiftOptions(rotationId: Long): List<ShiftEntity> =
        calendarRepository.getAllShiftsUsedInRotation(rotationId)

    /**
     * Die Wochentage (0=Mo..6=So), an denen ein Stundenplan-Job tatsächlich Unterrichtsstunden
     * hat — zur Auswahl, falls ein Alarm statt an jeden Unterrichtstag an einen einzelnen
     * Wochentag gekoppelt werden soll (siehe [AlarmEntity.linkedTimetableWeekday]).
     */
    suspend fun timetableWeekdayOptions(jobId: Long): List<Int> =
        calendarRepository.getLessonsForJobOnce(jobId).map { it.weekday }.distinct().sorted()

    fun createAlarm(hour: Int, minute: Int, repeatDays: Int = 0) {
        viewModelScope.launch {
            val alarm = AlarmEntity(hour = hour, minute = minute, repeatDays = repeatDays, enabled = true)
            val id = alarmRepository.save(alarm)
            alarmScheduler.schedule(alarm.copy(id = id))
        }
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(enabled = enabled)
            alarmRepository.save(updated)
            if (enabled) alarmScheduler.schedule(updated) else alarmScheduler.cancel(updated.id)
        }
    }

    fun save(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmRepository.save(alarm)
            if (alarm.enabled) alarmScheduler.schedule(alarm) else alarmScheduler.cancel(alarm.id)
        }
    }

    fun delete(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmScheduler.cancel(alarm.id)
            alarmRepository.delete(alarm)
        }
    }

    val voiceCommandsEnabled: StateFlow<Boolean> = appPreferences.voiceCommandsEnabled

    fun setVoiceCommandsEnabled(enabled: Boolean) {
        appPreferences.setVoiceCommandsEnabled(enabled)
    }

    private val _voiceFeedback = MutableStateFlow<String?>(null)
    val voiceFeedback: StateFlow<String?> = _voiceFeedback

    /** Von Androids Spracherkenner transkribierter Text — Timer/Stoppuhr/einmaliger Wecker per Sprache. */
    fun handleVoiceCommand(spokenText: String) {
        viewModelScope.launch {
            _voiceFeedback.value = voiceCommandExecutor.execute(spokenText)
        }
    }

    fun clearVoiceFeedback() {
        _voiceFeedback.value = null
    }
}
