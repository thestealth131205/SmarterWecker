package de.letheapp.nocturn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.EXTRA_OPEN_CALENDAR
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.R
import de.letheapp.nocturn.data.repository.AgendaEntry
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private const val MAX_AGENDA_ITEMS = 4

private data class CalendarAgendaRowIds(val rowId: Int, val dotId: Int, val timeId: Int, val labelId: Int)

private val ROW_SPECS = listOf(
    CalendarAgendaRowIds(R.id.widget_calendar_row1, R.id.widget_calendar_dot1, R.id.widget_calendar_time1, R.id.widget_calendar_label1),
    CalendarAgendaRowIds(R.id.widget_calendar_row2, R.id.widget_calendar_dot2, R.id.widget_calendar_time2, R.id.widget_calendar_label2),
    CalendarAgendaRowIds(R.id.widget_calendar_row3, R.id.widget_calendar_dot3, R.id.widget_calendar_time3, R.id.widget_calendar_label3),
    CalendarAgendaRowIds(R.id.widget_calendar_row4, R.id.widget_calendar_dot4, R.id.widget_calendar_time4, R.id.widget_calendar_label4)
)

private val WEEKDAY_FORMATTER = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)

/**
 * Kalender-/Termine-Widget (CLAUDE.md Kernfunktion 7): zeigt die nächsten anstehenden
 * Schichten (aus Rotationen) und freien Termine als kleine Agenda. Nutzt denselben
 * Hilt-`@AndroidEntryPoint`-auf-BroadcastReceiver-Ansatz wie [de.letheapp.nocturn.alarm.BootReceiver],
 * da `AppWidgetProvider` selbst ein `BroadcastReceiver` ist.
 */
@AndroidEntryPoint
class CalendarWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var calendarRepository: CalendarRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val agenda = calendarRepository.getUpcomingAgenda(MAX_AGENDA_ITEMS)
                appWidgetIds.forEach { updateWidget(context, appWidgetManager, it, agenda) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        agenda: List<AgendaEntry>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_calendar)
        val today = LocalDate.now()

        views.setViewVisibility(R.id.widget_calendar_empty, if (agenda.isEmpty()) View.VISIBLE else View.GONE)

        ROW_SPECS.forEachIndexed { index, spec ->
            val entry = agenda.getOrNull(index)
            if (entry == null) {
                views.setViewVisibility(spec.rowId, View.GONE)
            } else {
                views.setViewVisibility(spec.rowId, View.VISIBLE)
                views.setInt(spec.dotId, "setColorFilter", entry.colorArgb)
                val date = LocalDate.ofEpochDay(entry.epochDay)
                val dayLabel = if (date == today) {
                    context.getString(R.string.widget_calendar_today)
                } else {
                    WEEKDAY_FORMATTER.format(date)
                }
                views.setTextViewText(
                    spec.timeId,
                    if (entry.timeLabel != null) "$dayLabel, ${entry.timeLabel}" else dayLabel
                )
                views.setTextViewText(spec.labelId, entry.label)
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CALENDAR, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_calendar_root, pendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
