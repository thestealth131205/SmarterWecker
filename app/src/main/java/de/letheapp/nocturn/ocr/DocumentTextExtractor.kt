package de.letheapp.nocturn.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Extrahiert Rohtext aus einer per Datei-Auswahl (statt Kamera) gewählten Vorlage für den
 * Brief-/Terminzettel-Import (siehe [LetterImportScreen]). PDF/JPEG/PNG laufen weiterhin über die
 * lokale Tesseract-OCR-Engine (bei PDF: jede Seite erst als Bitmap gerendert, da weder eine
 * eingebettete-Text-Extraktion noch eine zusätzliche PDF-Bibliothek nötig sein soll — passt zur
 * FOSS-Minimalismus-Leitlinie und funktioniert unabhängig davon, ob die PDF echten Text oder nur
 * ein Scan-Bild enthält). TXT/RTF/DOCX brauchen kein OCR, ihr Text wird direkt aus dem
 * Dateiformat gelesen.
 */
object DocumentTextExtractor {

    private const val MAX_PDF_PAGES = 5

    suspend fun extractText(context: Context, uri: Uri, ocrEngine: TesseractOcrEngine): String {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val name = queryDisplayName(context, uri).orEmpty()
        return when {
            mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) ->
                extractFromPdf(context, uri, ocrEngine)
            mimeType.startsWith("image/") || name.matches(Regex(".*\\.(jpe?g|png)$", RegexOption.IGNORE_CASE)) ->
                extractFromImage(context, uri, ocrEngine)
            mimeType == "application/rtf" || mimeType == "text/rtf" || name.endsWith(".rtf", ignoreCase = true) ->
                extractFromRtf(context, uri)
            mimeType.contains("wordprocessingml") || name.endsWith(".docx", ignoreCase = true) ->
                extractFromDocx(context, uri)
            mimeType == "application/msword" || name.endsWith(".doc", ignoreCase = true) ->
                extractFromLegacyDoc(context, uri)
            else ->
                extractFromPlainText(context, uri)
        }
    }

    private suspend fun extractFromImage(context: Context, uri: Uri, ocrEngine: TesseractOcrEngine): String {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: return ""
        return ocrEngine.recognize(bitmap)
    }

    private suspend fun extractFromPdf(context: Context, uri: Uri, ocrEngine: TesseractOcrEngine): String {
        val fd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        val textBuilder = StringBuilder()
        fd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                for (index in 0 until pageCount) {
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width.coerceAtLeast(1) * 2,
                            page.height.coerceAtLeast(1) * 2,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        textBuilder.append(ocrEngine.recognize(bitmap)).append('\n')
                    }
                }
            }
        }
        return textBuilder.toString()
    }

    private fun extractFromPlainText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        return runCatching { bytes.toString(Charsets.UTF_8) }
            .getOrElse { bytes.toString(Charsets.ISO_8859_1) }
    }

    /** Entfernt RTF-Steuerwörter/-Gruppen grob per Regex statt einer vollen RTF-Bibliothek. */
    private fun extractFromRtf(context: Context, uri: Uri): String {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val rtf = runCatching { raw.toString(Charsets.ISO_8859_1) }.getOrElse { return "" }
        var text = rtf
            .replace(Regex("\\\\par[d]?\\b"), "\n")
            .replace(Regex("\\{\\\\\\*[^{}]*\\}"), "")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d*\\s?"), "")
            .replace(Regex("[{}]"), "")
            .replace(Regex("\\\\'[0-9a-fA-F]{2}"), "")
        text = text.replace(Regex("[ \\t]{2,}"), " ")
        return text.trim()
    }

    /** Liest word/document.xml aus dem .docx-Zip-Container und entfernt die XML-Tags. */
    private fun extractFromDocx(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: return ""
        var xml: String? = null
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        xml = out.toString("UTF-8")
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val content = xml ?: return ""
        return content
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<w:tab/>"), "\t")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    /**
     * Altes binäres .doc-Format (OLE2) hat keine einfache Textstruktur wie .docx. Statt einer
     * zusätzlichen Bibliothek nur dafür wird grob jede Folge aus mind. 4 druckbaren Zeichen aus
     * den Rohbytes herausgefiltert (Word legt Fließtext in solchen Blöcken ab) — liefert kein
     * perfektes Ergebnis, reicht aber als Rohtext für die anschließende Regex-Erkennung, und der
     * Nutzer bestätigt/korrigiert das Ergebnis ohnehin immer von Hand.
     */
    private fun extractFromLegacyDoc(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val raw = runCatching { bytes.toString(Charsets.ISO_8859_1) }.getOrElse { return "" }
        val matches = Regex("[\\x20-\\x7EÄÖÜäöüß]{4,}").findAll(raw).map { it.value }
        return matches.joinToString("\n")
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
        }.getOrNull()
    }
}
