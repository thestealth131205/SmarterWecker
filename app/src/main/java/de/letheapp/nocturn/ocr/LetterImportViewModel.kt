package de.letheapp.nocturn.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hält den OCR-/Parse-Fortschritt für den Brief-/Terminzettel-Import (siehe
 * [LetterImportScreen]) — sowohl per Kamera-Foto als auch per Datei-Auswahl
 * (PDF/JPEG/PNG/DOCX/DOC/RTF/TXT, siehe [DocumentTextExtractor]). Ergebnis ist immer nur ein
 * Vorschlag, den der Nutzer in der normalen Termin-Kontrollmaske noch bestätigt oder korrigiert.
 */
@HiltViewModel
class LetterImportViewModel @Inject constructor(
    private val ocrEngine: TesseractOcrEngine
) : ViewModel() {

    var isProcessing by mutableStateOf(false)
        private set
    var parsedResult by mutableStateOf<ParsedAppointmentResult?>(null)
        private set

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            isProcessing = true
            val recognizedText = ocrEngine.recognize(bitmap)
            parsedResult = AppointmentTextParser.parse(recognizedText)
            isProcessing = false
        }
    }

    fun processFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            isProcessing = true
            val recognizedText = DocumentTextExtractor.extractText(context, uri, ocrEngine)
            parsedResult = AppointmentTextParser.parse(recognizedText)
            isProcessing = false
        }
    }
}
