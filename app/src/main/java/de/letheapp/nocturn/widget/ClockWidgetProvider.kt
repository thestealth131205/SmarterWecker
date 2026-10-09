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
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.R
import de.letheapp.nocturn.data.repository.AgendaEntry
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.data.repository.WidgetAgenda
import de.letheapp.nocturn.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private const val ACTION_TICK = "de.letheapp.nocturn.widget.ACTION_CLOCK_TICK"
private const val AGENDA_SHOWN_COUNT = 2
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val NEXT_ALARM_FORMATTER = DateTimeFormatter.ofPattern("EEE, HH:mm", Locale.GERMAN)
private val AGENDA_WEEKDAY_FORMATTER = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)

private data class ClockAgendaRowIds(val rowId: Int, val dotId: Int, val textId: Int)

private val AGENDA_ROW_SPECS = listOf(
    ClockAgendaRowIds(R.id.widget_clock_agenda_row1, R.id.widget_clock_agenda_dot1, R.id.widget_clock_agenda_text1),
    ClockAgendaRowIds(R.id.widget_clock_agenda_row2, R.id.widget_clock_agenda_dot2, R.id.widget_clock_agenda_text2)
)

/**
 * Uhr-Widget (CLAUDE.md Kernfunktion 7): Datum/Uhrzeit/nächster Alarm, transparent über dem
 * Homescreen-Hintergrund. "Nächster Alarm" kommt direkt von [AlarmManager.getNextAlarmClock]
 * statt aus der eigenen DB, weil [de.letheapp.nocturn.alarm.AlarmScheduler] Alarme bereits über
 * `setAlarmClock` plant — das System kennt die nächste Weckzeit also ohnehin schon. Zusätzlich
 * zeigt es das aktuelle Wetter (OpenWeatherMap, siehe [WeatherRepository]) und schmal die
 * nächsten [AGENDA_SHOWN_COUNT] anstehenden Kalendereinträge, mit einer "+N"-Angabe, falls am
 * Tag des zuletzt gezeigten Eintrags noch weitere anstehen.
 *
 * Da offizielle Widget-Updates (`updatePeriodMillis`) minütlich nicht zuverlässig genug sind,
 * stößt sich das Widget selbst per [AlarmManager.setRepeating] jede Minute neu an. Das Wetter
 * wird dabei nicht bei jedem Tick neu abgefragt, sondern von [WeatherRepository] selbst 30
 * Minuten lang gecacht.
 */
@AndroidEntryPoint
class ClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var calendarRepository: CalendarRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { refreshWidget(context, appWidgetManager, it) }
        scheduleTicks(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
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
                val weather = runCatching { weatherRepository.currentWeather() }.getOrNull()
                val agenda = runCatching { calendarRepository.getUpcomingAgendaForWidget(AGENDA_SHOWN_COUNT) }
                    .getOrNull() ?: WidgetAgenda(emptyList(), 0)
                updateWidget(context, appWidgetManager, widgetId, weather?.let { "${it.emoji} ${it.temperatureCelsius}°" }, agenda)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        weatherLabel: String?,
        agenda: WidgetAgenda
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        val now = LocalDateTime.now()

        views.setTextViewText(R.id.widget_clock_date, DATE_FORMATTER.format(now))
        views.setTextViewText(R.id.widget_clock_time, TIME_FORMATTER.format(now))

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarm = alarmManager.nextAlarmClock
        if (nextAlarm != null) {
            val nextAlarmTime = Instant.ofEpochMilli(nextAlarm.triggerTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            views.setTextViewText(R.id.widget_clock_next_alarm, NEXT_ALARM_FORMATTER.format(nextAlarmTime))
            views.setViewVisibility(R.id.widget_clock_next_alarm_row, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_clock_next_alarm_row, View.GONE)
        }

        if (weatherLabel != null) {
            views.setTextViewText(R.id.widget_clock_weather, weatherLabel)
            views.setViewVisibility(R.id.widget_clock_weather, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_clock_weather, View.GONE)
        }

        applyAgenda(views, agenda)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_clock_root, openAppPendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun applyAgenda(views: RemoteViews, agenda: WidgetAgenda) {
        val today = LocalDate.now()
        AGENDA_ROW_SPECS.forEachIndexed { index, spec ->
            val entry = agenda.entries.getOrNull(index)
            if (entry == null) {
                views.setViewVisibility(spec.rowId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(spec.rowId, View.VISIBLE)
            views.setInt(spec.dotId, "setColorFilter", entry.colorArgb)

            val isLastShown = index == agenda.entries.lastIndex
            val extraSuffix = if (isLastShown && agenda.extraCountForLastDay > 0) {
                " (+${agenda.extraCountForLastDay})"
            } else {
                ""
            }
            views.setTextViewText(spec.textId, formatAgendaText(entry, today) + extraSuffix)
        }
    }

    private fun formatAgendaText(entry: AgendaEntry, today: LocalDate): String {
        val date = LocalDate.ofEpochDay(entry.epochDay)
        val dayLabel = if (date == today) "Heute" else AGENDA_WEEKDAY_FORMATTER.format(date)
        val timePart = entry.timeLabel?.let { ", $it" }.orEmpty()
        return "$dayLabel$timePart: ${entry.label}"
    }

    private fun scheduleTicks(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val firstTriggerAt = (System.currentTimeMillis() / 60_000L + 1) * 60_000L
        alarmManager.setRepeating(AlarmManager.RTC, firstTriggerAt, 60_000L, tickPendingIntent(context))
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockWidgetProvider::class.java).apply { action = ACTION_TICK }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
