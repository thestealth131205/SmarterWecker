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
private const val TESSDATA_URL = "https://github.com/tesseract-ocr/tessdata/raw/main/$LANGUAGE.traineddata"
private val ROTATIONS_TO_TRY = intArrayOf(0, 90, 180, 270)

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

    /**
     * Ein fotografierter Zettel liegt selten exakt im Hochformat vor Tesseract — Text, der um
     * 90/180/270° gedreht ist, liefert sonst nur Zufallsfragmente. Da es ohne zusätzliche
     * osd.traineddata keine eingebaute Orientierungserkennung gibt, wird hier stattdessen bei
     * allen vier Rotationen erkannt und die Variante mit der höchsten mittleren Tesseract-
     * Konfidenz behalten — kostet das Vierfache an Rechenzeit, fällt bei einem einmaligen
     * Foto-Import aber nicht ins Gewicht.
     */
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (!ensureTrainedDataAvailable()) return@withContext ""
        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataDir.absolutePath, LANGUAGE)) return@withContext ""
            var bestText = ""
            var bestConfidence = -1
            for (rotationDegrees in ROTATIONS_TO_TRY) {
                val rotated = if (rotationDegrees == 0) bitmap else rotateBitmap(bitmap, rotationDegrees)
                tess.setImage(normalizeForOcr(rotated))
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
