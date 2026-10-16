package de.letheapp.nocturn.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.calendar.RotationOption
import de.letheapp.nocturn.data.db.HolidaySettingsEntity
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.WearSourceType
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.holidays.HolidayRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ganzer Zustand des "WearOS-Verwaltung"-Screens, siehe [WearSettingsScreen]. */
data class WearSettingsState(
    val jobs: List<JobEntity> = emptyList(),
    val rotations: List<RotationOption> = emptyList(),
    val enabledJobIds: Set<Long> = emptySet(),
    val enabledRotationIds: Set<Long> = emptySet(),
    val birthdaysEnabled: Boolean = false,
    val publicHolidaysEnabled: Boolean = false,
    val schoolHolidaysEnabled: Boolean = false,
    /** Null = kein Bundesland in "Ferien & Feiertage" hinterlegt — Feiertage/Ferien können dann
     *  hier zwar aktiviert werden, es gibt aber noch keine Daten zum Übertragen. */
    val holidaySubdivisionLabel: String? = null
)

@HiltViewModel
class WearSettingsViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository,
    private val visibleSourceRepository: WearVisibleSourceRepository,
    private val wearSyncManager: WearSyncManager
) : ViewModel() {

    /** Jobs/Rotationen + ihre aktivierten IDs — erste Hälfte, da `combine` nur bis zu 5 Flows an einem Stück unterstützt. */
    private val jobsAndRotations = combine(
        calendarRepository.observeJobs(),
        calendarRepository.observeAllRotations(),
        visibleSourceRepository.observeEnabledKeys(WearSourceType.JOB),
        visibleSourceRepository.observeEnabledKeys(WearSourceType.ROTATION)
    ) { jobs, rotations, enabledJobIds, enabledRotationIds ->
        val jobsById = jobs.associateBy { it.id }
        JobsAndRotations(
            jobs = jobs,
            rotations = rotations.map { RotationOption(it.id, it.name, jobsById[it.jobId]?.name.orEmpty()) },
            enabledJobIds = enabledJobIds,
            enabledRotationIds = enabledRotationIds
        )
    }

    /** Geburtstage/Feiertage/Ferien-Schalter + das für "Ferien & Feiertage" hinterlegte Bundesland. */
    private val singletonToggles = combine(
        visibleSourceRepository.observeSingletonEnabled(WearSourceType.BIRTHDAYS),
        visibleSourceRepository.observeSingletonEnabled(WearSourceType.PUBLIC_HOLIDAYS),
        visibleSourceRepository.observeSingletonEnabled(WearSourceType.SCHOOL_HOLIDAYS),
        holidayRepository.observeSettings()
    ) { birthdays, publicHolidays, schoolHolidays, holidaySettings ->
        SingletonToggles(birthdays, publicHolidays, schoolHolidays, holidaySettings)
    }

    val state: StateFlow<WearSettingsState> = combine(jobsAndRotations, singletonToggles) { jr, st ->
        WearSettingsState(
            jobs = jr.jobs,
            rotations = jr.rotations,
            enabledJobIds = jr.enabledJobIds,
            enabledRotationIds = jr.enabledRotationIds,
            birthdaysEnabled = st.birthdaysEnabled,
            publicHolidaysEnabled = st.publicHolidaysEnabled,
            schoolHolidaysEnabled = st.schoolHolidaysEnabled,
            holidaySubdivisionLabel = st.holidaySettings?.subdivisionLabel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearSettingsState())

    private data class JobsAndRotations(
        val jobs: List<JobEntity>,
        val rotations: List<RotationOption>,
        val enabledJobIds: Set<Long>,
        val enabledRotationIds: Set<Long>
    )

    private data class SingletonToggles(
        val birthdaysEnabled: Boolean,
        val publicHolidaysEnabled: Boolean,
        val schoolHolidaysEnabled: Boolean,
        val holidaySettings: HolidaySettingsEntity?
    )

    fun setJobEnabled(jobId: Long, enabled: Boolean) = toggle {
        visibleSourceRepository.setJobOrRotationEnabled(WearSourceType.JOB, jobId, enabled)
    }

    fun setRotationEnabled(rotationId: Long, enabled: Boolean) = toggle {
        visibleSourceRepository.setJobOrRotationEnabled(WearSourceType.ROTATION, rotationId, enabled)
    }

    fun setBirthdaysEnabled(enabled: Boolean) = toggle {
        visibleSourceRepository.setSingletonEnabled(WearSourceType.BIRTHDAYS, enabled)
    }

    fun setPublicHolidaysEnabled(enabled: Boolean) = toggle {
        visibleSourceRepository.setSingletonEnabled(WearSourceType.PUBLIC_HOLIDAYS, enabled)
    }

    fun setSchoolHolidaysEnabled(enabled: Boolean) = toggle {
        visibleSourceRepository.setSingletonEnabled(WearSourceType.SCHOOL_HOLIDAYS, enabled)
    }

    /** Jede Umschaltung soll sich sofort auf der Uhr bemerkbar machen, nicht erst beim nächsten
     *  periodischen [WearSyncWorker]-Lauf. */
    private fun toggle(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            wearSyncManager.syncNow()
        }
    }
}
