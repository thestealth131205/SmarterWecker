package de.letheapp.nocturn.ocr

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.calendar.AddAppointmentScreen
import java.io.File

/** Mime-Typen, aus denen bei der Datei-Auswahl (statt Foto) ein Termin erkannt werden kann. */
private val LETTER_IMPORT_FILE_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/rtf",
    "text/rtf",
    "text/plain",
    "image/jpeg",
    "image/png"
)

/**
 * Brief-/Terminzettel-Import (CLAUDE.md Kernfunktion 6): Fragt beim Betreten zunächst, ob per
 * Kamera fotografiert oder stattdessen eine vorhandene Datei (PDF/Foto/DOCX/DOC/RTF/TXT)
 * ausgewählt werden soll. Danach lokale OCR bzw. direkte Text-Extraktion
 * ([DocumentTextExtractor]) + deterministisches Parsing ([AppointmentTextParser]) ohne LLM. Das
 * Ergebnis landet direkt in der normalen Termin-Kontrollmaske ([AddAppointmentScreen]) zur
 * Bestätigung/Korrektur — es wird nie automatisch gespeichert.
 */
@Composable
fun LetterImportScreen(
    onBack: () -> Unit,
    viewModel: LetterImportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var showSourceDialog by remember { mutableStateOf(true) }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) viewModel.processImage(bitmap) else onBack()
        } else {
            onBack()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.processFile(context, uri)
        } else {
            onBack()
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Termin erkennen") },
            text = { Text("Soll der Termin von einem Foto oder aus einer vorhandenen Datei (PDF, Word, RTF, TXT, Bild) erkannt werden?") },
            confirmButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    val uri = createLetterCaptureUri(context)
                    pendingCaptureUri = uri
                    captureLauncher.launch(uri)
                }) {
                    Text("Foto aufnehmen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    filePickerLauncher.launch(LETTER_IMPORT_FILE_MIME_TYPES)
                }) {
                    Text("Datei wählen")
                }
            }
        )
    }

    val parsed = viewModel.parsedResult
    if (parsed != null) {
        LaunchedEffect(parsed) {
            if (!parsed.textRecognized) {
                Toast.makeText(context, "Kein Text erkannt – bitte Angaben prüfen", Toast.LENGTH_LONG).show()
            }
        }
        AddAppointmentScreen(
            initialTitle = parsed.title,
            initialLocation = parsed.location,
            initialDescription = parsed.description,
            initialBeginMillis = parsed.beginMillis,
            initialEndMillis = parsed.endMillis,
            initialAllDay = parsed.allDay,
            onSaved = onBack,
            onCancelled = onBack
        )
    } else if (!showSourceDialog) {
        Scaffold { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                if (viewModel.isProcessing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Erkenne Termin…", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun createLetterCaptureUri(context: android.content.Context): Uri {
    // Nutzt denselben in file_paths.xml deklarierten Cache-Ordner wie der bestehende
    // Stundenplan-Foto-Import (siehe PhotoImportScreen.createCaptureUri) statt eines eigenen
    // FileProvider-Pfad-Eintrags.
    val dir = File(context.cacheDir, "ocr_captures").apply { mkdirs() }
    val file = File(dir, "letter_capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
