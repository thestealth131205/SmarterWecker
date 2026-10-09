package de.letheapp.nocturn.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import de.letheapp.nocturn.calendar.PUBLIC_HOLIDAY_COLOR_ARGB
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Zeichnet Monatsübersichten direkt über [Canvas]/[Paint] in eine [Bitmap] — bewusst ohne
 * Compose-Snapshot-Bibliothek, da Android hierfür bereits alles Nötige mitbringt (passend zur
 * FOSS-Minimalismus-Leitlinie). Dieselben Bitmaps werden je nach gewähltem Export-Format
 * (siehe [CalendarExportService]) als PDF-Seite oder als JPG/PNG-Datei verwendet.
 */
object CalendarPageRenderer {

    private val WEEKDAY_HEADERS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    /** Eine einzelne Monatsseite in voller Seitengröße (Modus "Jeder Monat auf eine eigene Seite"). */
    fun renderMonthPage(
        month: YearMonth,
        entriesByDay: Map<LocalDate, List<ExportDayEntry>>,
        widthPx: Int,
        heightPx: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = heightPx * 0.032f
            isFakeBoldText = true
        }
        val title = month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN).replaceFirstChar { it.uppercase() } + " ${month.year}"
        canvas.drawText(title, widthPx * 0.04f, heightPx * 0.06f, titlePaint)

        val gridTop = heightPx * 0.1f
        val gridBottom = heightPx * 0.92f
        val legendTop = heightPx * 0.93f
        drawMonthGrid(
            canvas = canvas,
            month = month,
            entriesByDay = entriesByDay,
            bounds = RectF(widthPx * 0.04f, gridTop, widthPx * 0.96f, gridBottom),
            headerTextSize = heightPx * 0.018f,
            dayNumberTextSize = heightPx * 0.02f,
            labelTextSize = heightPx * 0.012f,
            maxLabelLines = 3
        )
        drawLegend(canvas, entriesByDay, widthPx * 0.04f, legendTop, widthPx * 0.96f, heightPx * 0.012f)

        return bitmap
    }

    /** Alle Monate verkleinert auf einer einzigen Seite (Modus "Alles auf eine Seite"). */
    fun renderOverviewPage(
        months: List<YearMonth>,
        entriesByDay: Map<LocalDate, List<ExportDayEntry>>,
        widthPx: Int,
        heightPx: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        if (months.isEmpty()) return bitmap

        val columns = Math.ceil(Math.sqrt(months.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = Math.ceil(months.size / columns.toDouble()).toInt().coerceAtLeast(1)

        val legendHeight = heightPx * 0.05f
        val gridAreaHeight = heightPx - legendHeight
        val cellWidth = widthPx.toFloat() / columns
        val cellHeight = gridAreaHeight / rows
        val padding = cellWidth * 0.02f

        months.forEachIndexed { index, month ->
            val col = index % columns
            val row = index / columns
            val left = col * cellWidth + padding
            val top = row * cellHeight + padding
            val right = (col + 1) * cellWidth - padding
            val bottom = (row + 1) * cellHeight - padding

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = cellHeight * 0.07f
                isFakeBoldText = true
            }
            val title = month.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN).replaceFirstChar { it.uppercase() } + " ${month.year}"
            canvas.drawText(title, left, top + cellHeight * 0.06f, titlePaint)

            drawMonthGrid(
                canvas = canvas,
                month = month,
                entriesByDay = entriesByDay,
                bounds = RectF(left, top + cellHeight * 0.1f, right, bottom),
                headerTextSize = cellHeight * 0.03f,
                dayNumberTextSize = cellHeight * 0.032f,
                labelTextSize = 0f,
                maxLabelLines = 0
            )
        }

        drawLegend(canvas, entriesByDay, widthPx * 0.04f, heightPx - legendHeight + heightPx * 0.01f, widthPx * 0.96f, heightPx * 0.014f)

        return bitmap
    }

    private fun drawMonthGrid(
        canvas: Canvas,
        month: YearMonth,
        entriesByDay: Map<LocalDate, List<ExportDayEntry>>,
        bounds: RectF,
        headerTextSize: Float,
        dayNumberTextSize: Float,
        labelTextSize: Float,
        maxLabelLines: Int
    ) {
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = headerTextSize
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = dayNumberTextSize
        }
        val holidayDayNumberPaint = Paint(dayNumberPaint).apply { color = Color.WHITE }
        val holidayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PUBLIC_HOLIDAY_COLOR_ARGB }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = labelTextSize
        }

        val headerHeight = headerTextSize * 1.6f
        val gridTop = bounds.top + headerHeight
        val cellWidth = bounds.width() / 7f
        val cellHeight = (bounds.bottom - gridTop) / 6f

        WEEKDAY_HEADERS.forEachIndexed { index, label ->
            canvas.drawText(label, bounds.left + cellWidth * index + cellWidth / 2f, bounds.top + headerHeight * 0.7f, headerPaint)
        }

        val firstOfMonth = month.atDay(1)
        val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()

        for (dayIndex in 0 until daysInMonth) {
            val date = firstOfMonth.plusDays(dayIndex.toLong())
            val cellIndex = leadingBlanks + dayIndex
            val col = cellIndex % 7
            val row = cellIndex / 7
            val cellLeft = bounds.left + col * cellWidth
            val cellTop = gridTop + row * cellHeight

            canvas.drawRect(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight, linePaint)

            val entries = entriesByDay[date].orEmpty()
            val isPublicHoliday = entries.any { it.isPublicHoliday }
            val numberRadius = minOf(cellWidth, cellHeight) * 0.18f
            val numberCx = cellLeft + cellWidth * 0.18f
            val numberCy = cellTop + cellHeight * 0.22f
            if (isPublicHoliday) {
                canvas.drawCircle(numberCx, numberCy, numberRadius, holidayBgPaint)
            }
            canvas.drawText(
                date.dayOfMonth.toString(),
                numberCx - numberRadius * 0.5f,
                numberCy + numberRadius * 0.5f,
                if (isPublicHoliday) holidayDayNumberPaint else dayNumberPaint
            )

            if (entries.isNotEmpty()) {
                var dotX = cellLeft + cellWidth * 0.4f
                val dotY = cellTop + cellHeight * 0.22f
                val dotRadius = minOf(cellWidth, cellHeight) * 0.045f
                entries.take(4).forEach { entry ->
                    dotPaint.color = entry.colorArgb
                    canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
                    dotX += dotRadius * 3f
                }

                if (maxLabelLines > 0 && labelTextSize > 0f) {
                    entries.take(maxLabelLines).forEachIndexed { lineIndex, entry ->
                        canvas.drawText(
                            truncate(entry.label, 16),
                            cellLeft + cellWidth * 0.08f,
                            cellTop + cellHeight * 0.48f + lineIndex * labelTextSize * 1.3f,
                            labelPaint
                        )
                    }
                }
            }
        }
    }

    private fun drawLegend(
        canvas: Canvas,
        entriesByDay: Map<LocalDate, List<ExportDayEntry>>,
        left: Float,
        top: Float,
        right: Float,
        textSize: Float
    ) {
        val allDistinct = entriesByDay.values.flatten()
            .distinctBy { it.label to it.colorArgb }
            .sortedBy { it.label }
        if (allDistinct.isEmpty()) return
        val distinctEntries = allDistinct.take(MAX_LEGEND_ENTRIES)
        val omittedCount = allDistinct.size - distinctEntries.size

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            this.textSize = textSize
        }
        val dotRadius = textSize * 0.45f
        val rowHeight = textSize * 1.8f
        var x = left
        var y = top + rowHeight

        distinctEntries.forEach { entry ->
            val label = truncate(entry.label, 24)
            val entryWidth = dotRadius * 3f + textPaint.measureText(label) + textSize * 1.5f
            if (x + entryWidth > right) {
                x = left
                y += rowHeight
            }
            dotPaint.color = entry.colorArgb
            canvas.drawCircle(x + dotRadius, y - textSize * 0.35f, dotRadius, dotPaint)
            canvas.drawText(label, x + dotRadius * 3f, y, textPaint)
            x += entryWidth
        }

        if (omittedCount > 0) {
            val suffix = "+$omittedCount weitere"
            if (x + textPaint.measureText(suffix) > right) {
                x = left
                y += rowHeight
            }
            canvas.drawText(suffix, x, y, textPaint)
        }
    }

    private const val MAX_LEGEND_ENTRIES = 24

    private fun truncate(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.take(maxLength - 1) + "…"
}
