package de.letheapp.nocturn.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class ExportLayout { ONE_PAGE_TOTAL, ONE_PAGE_PER_MONTH }

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

/** A4 bei ca. 150dpi — ausreichend scharf zum Drucken, ohne unnötig große Dateien zu erzeugen. */
private const val PAGE_WIDTH_PX = 1240
private const val PAGE_HEIGHT_PX = 1754

/**
 * Erzeugt aus den Kalendereinträgen eines frei wählbaren Zeitraums druckbare Dateien
 * (PDF, JPG oder PNG), wahlweise als eine Übersichtsseite oder eine Seite pro Monat
 * (siehe [CalendarExportScreen]). Dateien landen im App-Cache und werden von dort per
 * [androidx.core.content.FileProvider] geteilt — dieselbe Vorgehensweise wie beim
 * Foto-Import (siehe `ocr`-Package), nur für die Ausgabe statt die Aufnahme.
 */
class CalendarExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportRepository: CalendarExportRepository
) {
    suspend fun export(
        startDate: LocalDate,
        endDate: LocalDate,
        layout: ExportLayout,
        format: ExportFormat
    ): List<File> {
        val entriesByDay = exportRepository.getEntriesForRange(startDate.toEpochDay(), endDate.toEpochDay())
        val months = monthsBetween(startDate, endDate)
        val outputDir = File(context.cacheDir, "calendar_export").apply { mkdirs() }
        outputDir.listFiles()?.forEach { it.delete() }
        val timestamp = "${startDate}_${endDate}"

        return when (format) {
            ExportFormat.PDF -> listOf(exportPdf(months, entriesByDay, layout, outputDir, timestamp))
            ExportFormat.JPG, ExportFormat.PNG -> exportImages(months, entriesByDay, layout, outputDir, timestamp, format)
        }
    }

    private fun exportPdf(
        months: List<YearMonth>,
        entriesByDay: Map<LocalDate, List<ExportDayEntry>>,
        layout: ExportLayout,
        outputDir: File,
        timestamp: String
    ): File {
        val document = PdfDocument()
        val bitmaps = when (layout) {
            ExportLayout.ONE_PAGE_TOTAL -> listOf(
                CalendarPageRenderer.renderOverviewPage(months, entriesByDay, PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
            )
            ExportLayout.ONE_PAGE_PER_MONTH -> months.map { month ->
                CalendarPageRenderer.renderMonthPage(month, entriesByDay, PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
            }
        }

        bitmaps.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, index + 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
            bitmap.recycle()
        }

        val file = File(outputDir, "NocTurn_Kalender_$timestamp.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun exportImages(
        months: List<YearMonth>,
        entriesByDay: Map<LocalDate, List<ExportDayEntry>>,
        layout: ExportLayout,
        outputDir: File,
        timestamp: String,
        format: ExportFormat
    ): List<File> {
        val compressFormat = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (format == ExportFormat.PNG) 100 else 90

        fun writeBitmap(bitmap: Bitmap, name: String): File {
            val file = File(outputDir, "$name.${format.extension}")
            FileOutputStream(file).use { bitmap.compress(compressFormat, quality, it) }
            bitmap.recycle()
            return file
        }

        return when (layout) {
            ExportLayout.ONE_PAGE_TOTAL -> {
                val bitmap = CalendarPageRenderer.renderOverviewPage(months, entriesByDay, PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
                listOf(writeBitmap(bitmap, "NocTurn_Kalender_$timestamp"))
            }
            ExportLayout.ONE_PAGE_PER_MONTH -> months.map { month ->
                val bitmap = CalendarPageRenderer.renderMonthPage(month, entriesByDay, PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
                writeBitmap(bitmap, "NocTurn_Kalender_${month.year}_%02d".format(month.monthValue))
            }
        }
    }

    private fun monthsBetween(start: LocalDate, end: LocalDate): List<YearMonth> {
        val fromMonth = YearMonth.from(start)
        val toMonth = YearMonth.from(end)
        val result = mutableListOf<YearMonth>()
        var current = fromMonth
        while (!current.isAfter(toMonth)) {
            result.add(current)
            current = current.plusMonths(1)
        }
        return result
    }
}
