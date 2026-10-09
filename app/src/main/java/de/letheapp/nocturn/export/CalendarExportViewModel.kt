package de.letheapp.nocturn.export

import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CalendarExportUiState(
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusMonths(1),
    val layout: ExportLayout = ExportLayout.ONE_PAGE_PER_MONTH,
    val format: ExportFormat = ExportFormat.PDF,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val exportedFiles: List<Uri>? = null
)

@HiltViewModel
class CalendarExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportService: CalendarExportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarExportUiState())
    val uiState: StateFlow<CalendarExportUiState> = _uiState

    fun setStartDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(startDate = date, endDate = if (date.isAfter(state.endDate)) date else state.endDate)
        }
    }

    fun setEndDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(endDate = date, startDate = if (date.isBefore(state.startDate)) date else state.startDate)
        }
    }

    fun setLayout(layout: ExportLayout) {
        _uiState.update { it.copy(layout = layout) }
    }

    fun setFormat(format: ExportFormat) {
        _uiState.update { it.copy(format = format) }
    }

    fun consumeExportedFiles() {
        _uiState.update { it.copy(exportedFiles = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun export() {
        val state = _uiState.value
        if (state.endDate.isBefore(state.startDate)) {
            _uiState.update { it.copy(errorMessage = "Enddatum darf nicht vor dem Startdatum liegen.") }
            return
        }
        if (state.startDate.plusYears(3).isBefore(state.endDate)) {
            _uiState.update { it.copy(errorMessage = "Der Zeitraum darf höchstens 3 Jahre umfassen.") }
            return
        }

        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            runCatching {
                exportService.export(state.startDate, state.endDate, state.layout, state.format)
            }.onSuccess { files ->
                val uris = files.map { file ->
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                _uiState.update { it.copy(isExporting = false, exportedFiles = uris) }
            }.onFailure { error ->
                _uiState.update { it.copy(isExporting = false, errorMessage = "Export fehlgeschlagen: ${error.message}") }
            }
        }
    }
}
