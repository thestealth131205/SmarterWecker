package de.letheapp.nocturn.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject

private const val LANGUAGE = "deu"
private const val OSD_NAME = "osd"
private const val TESSDATA_BASE_URL = "https://github.com/tesseract-ocr/tessdata/raw/main"

/**
 * Eigens auf Termin-/Datums-/Uhrzeit-Erkennung fine-getuntes deu-Modell (siehe CLAUDE.md,
 * Abschnitt "Lokales Fine-Tuning von deu.traineddata"), gehostet unter letheapp.de statt der
 * offiziellen Tesseract-Quelle. [DEU_MODEL_VERSION] erzwingt bei bereits installierten Apps, die
 * schon die alte offizielle deu.traineddata zwischengespeichert haben, einmalig ein Neuladen.
 */
private const val DEU_TRAINEDDATA_URL = "https://letheapp.de/downloads/nocturn/ocr/deu_appointments.traineddata"
private const val DEU_MODEL_VERSION = 1
private val ROTATIONS_TO_TRY = intArrayOf(0, 90, 180, 270)
private const val MIN_CONFIDENCE_WITHOUT_FALLBACK = 40

/**
 * OCR über Tesseract4Android (Apache-2.0, FOSS-Alternative zu Googles closed-source ML Kit, siehe
 * CLAUDE.md Architektur-Abschnitt). Die Trainingsdaten (Sprache + Orientierungserkennung) werden
 * bewusst nicht im Repo mitgeführt, sondern beim ersten Foto-Import einmalig nachgeladen und lokal
 * zwischengespeichert — das hält das Repo klein. Die Orientierungserkennung (osd) kommt weiterhin
 * von der offiziellen tessdata-Quelle, die Sprachdatei (deu) vom eigenen, fine-getunten Modell.
 */
class TesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataDir: File get() = File(context.filesDir, "tesseract")

    suspend fun ensureTrainedDataAvailable(): Boolean = downloadIfMissing(
        name = LANGUAGE,
        url = DEU_TRAINEDDATA_URL,
        version = DEU_MODEL_VERSION,
    )

    /** osd.traineddata (Orientation and Script Detection) ist optional: fehlt sie (z. B. Download
     * fehlgeschlagen), fällt [recognize] automatisch auf die rotations-basierte Erkennung zurück. */
    private suspend fun ensureOsdDataAvailable(): Boolean = downloadIfMissing(
        name = OSD_NAME,
        url = "$TESSDATA_BASE_URL/$OSD_NAME.traineddata",
        version = 0,
    )

    private suspend fun downloadIfMissing(name: String, url: String, version: Int): Boolean =
        withContext(Dispatchers.IO) {
            val tessdataDir = File(dataDir, "tessdata").apply { mkdirs() }
            val file = File(tessdataDir, "$name.traineddata")
            val versionFile = File(tessdataDir, "$name.version")
            val cachedVersion = versionFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
            if (file.exists() && file.length() > 0 && cachedVersion == version) return@withContext true
            runCatching {
                URL(url).openStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                versionFile.writeText(version.toString())
            }.isSuccess
        }

    /**
     * Erster Versuch: volle automatische Seitenlayout-Analyse inkl. echter Orientierungs- und
     * Schrift-Erkennung (PSM_AUTO_OSD, braucht die zusätzlich geladene osd.traineddata) — ein
     * einzelner Durchlauf erkennt dabei auch gedrehten Text korrekt, statt wie zuvor blind alle
     * vier Rotationen durchzuprobieren.
     *
     * Reicht die Konfidenz dieses Durchlaufs nicht (z. B. bei unüblichen Layouts oder fehlender
     * osd.traineddata), fällt die Erkennung auf den bisherigen, robusteren Weg zurück: alle vier
     * 90°-Rotationen mit festem Einzelblock-Layout (PSM_SINGLE_BLOCK) durchprobieren und die
     * Variante mit der höchsten mittleren Tesseract-Konfidenz behalten.
     */
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (!ensureTrainedDataAvailable()) return@withContext ""
        val osdAvailable = ensureOsdDataAvailable()
        val normalized = normalizeForOcr(bitmap)

        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataDir.absolutePath, LANGUAGE)) return@withContext ""

            if (osdAvailable) {
                tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
                tess.setImage(normalized)
                val text = tess.utF8Text ?: ""
                if (text.isNotBlank() && tess.meanConfidence() >= MIN_CONFIDENCE_WITHOUT_FALLBACK) {
                    return@withContext text
                }
            }

            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            var bestText = ""
            var bestConfidence = -1
            for (rotationDegrees in ROTATIONS_TO_TRY) {
                val rotated = if (rotationDegrees == 0) normalized else rotateBitmap(normalized, rotationDegrees)
                tess.setImage(rotated)
                val text = tess.utF8Text ?: ""
                val confidence = tess.meanConfidence()
                if (text.isNotBlank() && confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestText = text
                }
            }
            bestText
        } finally {
            tess.recycle()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Tesseract ist auf dunkle Schrift auf hellem Grund trainiert. Fotografiert man z. B. einen
     * Stundenplan in einer Dark-Mode-App (heller Text auf dunklem Hintergrund), findet es ohne
     * Vorverarbeitung oft gar keinen Text. Hier wird daher in Graustufen gewandelt und bei
     * überwiegend dunklem Bild invertiert, bevor Tesseract das Bild sieht.
     */
    private fun normalizeForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var luminanceSum = 0L
        for (i in pixels.indices) {
            val p = pixels[i]
            val gray = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            pixels[i] = Color.rgb(gray, gray, gray)
            luminanceSum += gray
        }

        val averageLuminance = luminanceSum / pixels.size.toDouble()
        if (averageLuminance < 128) {
            for (i in pixels.indices) {
                val inverted = 255 - Color.red(pixels[i])
                pixels[i] = Color.rgb(inverted, inverted, inverted)
            }
        }

        val normalized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        normalized.setPixels(pixels, 0, width, 0, 0, width, height)
        return normalized
    }
}
