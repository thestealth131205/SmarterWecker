package de.letheapp.nocturn.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.EXTRA_OPEN_CALENDAR
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.R
import de.letheapp.nocturn.data.repository.AgendaEntry
import de.letheapp.nocturn.data.repository.AgendaEntryType
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private const val ACTION_TICK = "de.letheapp.nocturn.widget.ACTION_CALENDAR_TICK"
private const val ACTION_OPEN_CALENDAR_FROM_WIDGET = "de.letheapp.nocturn.widget.ACTION_CALENDAR_WIDGET_OPEN"
private const val TICK_INTERVAL_MILLIS = 20 * 60_000L
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
 *
 * Androids `updatePeriodMillis` wird vom System ohnehin auf minimal 30 Minuten gedeckelt, daher
 * stößt sich das Widget wie das Uhr-Widget ([ClockWidgetProvider]) selbst per
 * [AlarmManager.setRepeating] alle 20 Minuten neu an.
 */
@AndroidEntryPoint
class CalendarWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var calendarRepository: CalendarRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { refreshWidget(context, appWidgetManager, it) }
        scheduleTicks(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
            ids.forEach { refreshWidget(context, appWidgetManager, it) }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleTicks(context)
    }

    override fun onDisabled(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(tickPendingIntent(context))
    }

    private fun refreshWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val agenda = calendarRepository.getUpcomingAgenda(MAX_AGENDA_ITEMS)
                updateWidget(context, appWidgetManager, widgetId, agenda)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleTicks(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val firstTriggerAt = (System.currentTimeMillis() / TICK_INTERVAL_MILLIS + 1) * TICK_INTERVAL_MILLIS
        alarmManager.setRepeating(AlarmManager.RTC, firstTriggerAt, TICK_INTERVAL_MILLIS, tickPendingIntent(context))
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CalendarWidgetProvider::class.java).apply { action = ACTION_TICK }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

                if (entry.type == AgendaEntryType.SHIFT) {
                    // Eine eingetragene Schicht ist reine Hintergrundinfo und bekommt nur den
                    // farbigen Punkt, ein tatsächlicher Termin wird daneben mit Titel/Uhrzeit
                    // angezeigt statt in der kurzen Zeilenanzahl des Widgets zu verschwinden.
                    views.setViewVisibility(spec.timeId, View.GONE)
                    views.setViewVisibility(spec.labelId, View.GONE)
                } else {
                    views.setViewVisibility(spec.timeId, View.VISIBLE)
                    views.setViewVisibility(spec.labelId, View.VISIBLE)
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
        }

        // Eigene Action-Zeichenkette, damit dieser PendingIntent nicht mit dem bare
        // "Intent(context, MainActivity::class.java)" des Uhr-Widgets kollidiert - PendingIntent-
        // Gleichheit prüft Action/Component/Daten, nicht die Extras; ohne Unterscheidung hätte
        // FLAG_UPDATE_CURRENT dort live die Extras überschrieben.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CALENDAR_FROM_WIDGET
            putExtra(EXTRA_OPEN_CALENDAR, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_calendar_root, pendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
