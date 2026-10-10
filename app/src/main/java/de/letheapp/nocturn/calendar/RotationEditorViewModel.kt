package de.letheapp.nocturn.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor für eine Rotation: Nutzer trägt Tag für Tag die geltende Schicht ein, bis sich
 * das Muster wiederholen würde. Die Zykluslänge ist schlicht die Anzahl der eingegebenen
 * Tage. Die Rotation bleibt eine reine Vorlage ohne eigenes Startdatum — wann und wie lange
 * sie im Kalender gilt, wird erst später über die Kalender-Seitenleiste festgelegt.
 */
@HiltViewModel
class RotationEditorViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val jobId: Long = checkNotNull(savedStateHandle.get<String>("jobId")).toLong()
    private val rotationId: Long? = savedStateHandle.get<String>("rotationId")?.toLongOrNull()?.takeIf { it >= 0 }
    val isNew: Boolean = rotationId == null

    val shifts: StateFlow<List<ShiftEntity>> = calendarRepository.observeShiftsForJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var name by mutableStateOf("")
    var loaded by mutableStateOf(isNew)

    /** Pro Tag die Liste der an diesem Tag gleichzeitig geltenden Schichten — leer = frei. */
    val days = mutableStateListOf<List<Long>>()

    /** Stand beim Laden/letzten Speichern — Grundlage für [hasUnsavedChanges]. */
    private var savedName: String = ""
    private var savedDays: List<List<Long>> = emptyList()

    /**
     * true, wenn Name/Tage vom zuletzt gespeicherten bzw. geladenen Stand abweichen — verhindert,
     * dass ein Zurück-Tipp im Editor eine bereits vorgenommene Änderung (z. B. das Entfernen
     * eines versehentlich zugewiesenen Tages) stillschweigend verwirft, ohne dass der Nutzer es
     * merkt, siehe [RotationEditorScreen].
     */
    val hasUnsavedChanges: Boolean
        get() = name.trim() != savedName || days.toList() != savedDays

    /** Wie oft jede Schicht aktuell im Muster vorkommt — hilft, einen Fehltipp in der
     *  Mehrfachauswahl eines Tages (z. B. eine versehentlich mitausgewählte Schicht) vor dem
     *  Speichern zu erkennen, statt sie in einem großen Raster zu übersehen. */
    val shiftUsageCounts: Map<Long, Int>
        get() = days.flatten().groupingBy { it }.eachCount()

    init {
        val id = rotationId
        if (id != null) {
            viewModelScope.launch {
                calendarRepository.getRotation(id)?.let { rotation ->
                    name = rotation.name
                }
                val rotationDays = calendarRepository.getRotationDaysOnce(id)
                    .groupBy({ it.dayIndex }, { it.shiftId })
                val cycleLength = rotationDays.keys.size
                days.clear()
                days.addAll((0 until cycleLength).map { index -> rotationDays[index].orEmpty().filterNotNull() })
                savedName = name.trim()
                savedDays = days.toList()
                loaded = true
            }
        }
    }

    fun addDay() {
        days.add(emptyList())
    }

    /** Hängt einen neuen Tag mit genau dieser Schicht an — schnellerer Weg als "+ Tag" + Dialog. */
    fun addDayWithShift(shiftId: Long) {
        days.add(listOf(shiftId))
    }

    fun removeLastDay() {
        if (days.isNotEmpty()) days.removeAt(days.lastIndex)
    }

    /** Schaltet eine Schicht an [index] an/aus — mehrere Schichten können gleichzeitig gewählt sein. */
    fun toggleShift(index: Int, shiftId: Long) {
        if (index !in days.indices) return
        val current = days[index]
        days[index] = if (current.contains(shiftId)) current - shiftId else current + shiftId
    }

    /** Setzt den Tag auf "frei" zurück (keine Schicht). */
    fun clearDay(index: Int) {
        if (index in days.indices) days[index] = emptyList()
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank() || days.isEmpty()) return
        viewModelScope.launch {
            calendarRepository.saveRotation(
                jobId = jobId,
                rotationId = rotationId,
                name = name.trim(),
                dayShiftIds = days.toList()
            )
            savedName = name.trim()
            savedDays = days.toList()
            onDone()
        }
    }
}
