package de.letheapp.nocturn.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject

private const val LANGUAGE = "deu"
private const val TESSDATA_URL = "https://github.com/tesseract-ocr/tessdata/raw/main/$LANGUAGE.traineddata"

/**
 * OCR über Tesseract4Android (Apache-2.0, FOSS-Alternative zu Googles closed-source ML Kit, siehe
 * CLAUDE.md Architektur-Abschnitt). Die Sprachdaten (~15 MB) werden bewusst nicht im Repo
 * mitgeführt, sondern beim ersten Foto-Import einmalig von der offiziellen tessdata-Quelle
 * nachgeladen und lokal zwischengespeichert — das hält das Repo klein und lädt nur offiziell
 * von Tesseract selbst veröffentlichte, offene Trainingsdaten nach.
 */
class TesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataDir: File get() = File(context.filesDir, "tesseract")

    suspend fun ensureTrainedDataAvailable(): Boolean = withContext(Dispatchers.IO) {
        val tessdataDir = File(dataDir, "tessdata").apply { mkdirs() }
        val file = File(tessdataDir, "$LANGUAGE.traineddata")
        if (file.exists() && file.length() > 0) return@withContext true
        runCatching {
            URL(TESSDATA_URL).openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }.isSuccess
    }

    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (!ensureTrainedDataAvailable()) return@withContext ""
        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataDir.absolutePath, LANGUAGE)) return@withContext ""
            tess.setImage(bitmap)
            tess.utF8Text ?: ""
        } finally {
            tess.recycle()
        }
    }
}
