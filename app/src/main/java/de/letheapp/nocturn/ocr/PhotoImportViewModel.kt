package de.letheapp.nocturn.ocr

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.calendar.SHIFT_COLOR_PALETTE
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.LessonEntity
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Foto-Import (CLAUDE.md Kernfunktion 5): OCR-Text eines fotografierten Schicht-/Stundenplans
 * wird zeilenweise in [ParsedScheduleRow]s zerlegt, die der Nutzer vor der Übernahme noch
 * korrigieren kann. Je nach [de.letheapp.nocturn.data.db.JobEntity.mode] des Ziel-Jobs werden
 * daraus entweder Schichten + eine neue Rotation (Schichtarbeit) oder Unterrichtsstunden
 * (Schüler/Student-Modus) angelegt.
 */
@HiltViewModel
class PhotoImportViewModel @Inject constructor(
    private val ocrEngine: TesseractOcrEngine,
    private val calendarRepository: CalendarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val jobId: Long = checkNotNull(savedStateHandle.get<String>("jobId")).toLong()

    var isProcessing by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var importFinished by mutableStateOf(false)
    var jobMode by mutableStateOf(JobMode.SHIFT)
        private set

    val rows = mutableStateListOf<ParsedScheduleRow>()

    init {
        viewModelScope.launch {
            calendarRepository.getJob(jobId)?.let { job ->
                jobMode = runCatching { JobMode.valueOf(job.mode) }.getOrDefault(JobMode.SHIFT)
            }
        }
    }

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            isProcessing = true
            errorMessage = null
            val recognizedText = ocrEngine.recognize(bitmap)
            val parsedRows = TimetableTextParser.parse(recognizedText)
            if (parsedRows.isEmpty()) {
                errorMessage = "Es konnten keine Zeiten erkannt werden. Bitte erneut fotografieren " +
                    "(hell, gerade, nur die Tabelle im Bild) oder Einträge manuell anlegen."
            }
            rows.clear()
            rows.addAll(
                parsedRows.mapIndexed { index, row ->
                    row.copy(colorArgb = SHIFT_COLOR_PALETTE[index % SHIFT_COLOR_PALETTE.size].toArgb())
                }
            )
            isProcessing = false
        }
    }

    fun updateRow(updated: ParsedScheduleRow) {
        val index = rows.indexOfFirst { it.id == updated.id }
        if (index >= 0) rows[index] = updated
    }

    fun removeRow(row: ParsedScheduleRow) {
        rows.removeAll { it.id == row.id }
    }

    fun confirmImport() {
        viewModelScope.launch {
            val includedRows = rows.filter { it.included }
            if (includedRows.isEmpty()) return@launch

            if (jobMode == JobMode.STUDENT) {
                includedRows.forEach { row ->
                    calendarRepository.saveLesson(
                        LessonEntity(
                            jobId = jobId,
                            weekday = row.weekday,
                            name = row.label,
                            colorArgb = row.colorArgb,
                            startHour = row.startHour,
                            startMinute = row.startMinute,
                            durationMinutes = row.durationMinutes
                        )
                    )
                }
            } else {
                // Jede erkannte Zeile wird eine Schicht; die erkannte Reihenfolge wird 1:1 zur
                // Tag-für-Tag-Rotation — ein Foto einer mehrtägigen Dienstplan-Tabelle entspricht
                // damit direkt dem Rotationsmuster, das sonst im Rotations-Editor eingegeben wird.
                val shiftIds = includedRows.map { row ->
                    calendarRepository.saveShift(
                        ShiftEntity(
                            jobId = jobId,
                            name = row.label,
                            colorArgb = row.colorArgb,
                            startHour = row.startHour,
                            startMinute = row.startMinute,
                            durationMinutes = row.durationMinutes
                        )
                    )
                }
                calendarRepository.saveRotation(
                    jobId = jobId,
                    rotationId = null,
                    name = "Foto-Import ${LocalDate.now()}",
                    dayShiftIds = shiftIds.map { listOf(it) }
                )
            }
            importFinished = true
        }
    }
}
