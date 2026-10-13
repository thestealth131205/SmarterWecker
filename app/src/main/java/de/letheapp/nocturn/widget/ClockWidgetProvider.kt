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
import de.letheapp.nocturn.EXTRA_OPEN_WEATHER
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.R
import de.letheapp.nocturn.data.repository.AgendaEntry
import de.letheapp.nocturn.data.repository.AgendaEntryType
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

private const val ACTION_CLOCK_TICK = "de.letheapp.nocturn.widget.ACTION_CLOCK_TICK"
private const val ACTION_DATA_TICK = "de.letheapp.nocturn.widget.ACTION_DATA_TICK"
private const val CLOCK_TICK_INTERVAL_MILLIS = 60_000L
private const val DATA_TICK_INTERVAL_MILLIS = 20 * 60_000L
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
 * Da offizielle Widget-Updates (`updatePeriodMillis`) nicht zuverlässig genug sind, stößt sich das
 * Widget selbst per [AlarmManager.setRepeating] an — dabei getrennt in zwei Takte: Datum/Uhrzeit
 * aktualisieren sich jede Minute per günstigem [AppWidgetManager.partiallyUpdateAppWidget] (kein
 * Netzwerk-/DB-Zugriff nötig), Wetter und Kalender-Agenda dagegen nur alle 20 Minuten über den
 * vollen, asynchronen Refresh. Das Wetter wird dabei nicht bei jedem Tick neu abgefragt, sondern
 * von [WeatherRepository] selbst 30 Minuten lang gecacht.
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
        when (intent.action) {
            ACTION_CLOCK_TICK -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
                ids.forEach { refreshClockOnly(context, appWidgetManager, it) }
                scheduleNextClockTick(context)
            }
            ACTION_DATA_TICK -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
                ids.forEach { refreshWidget(context, appWidgetManager, it) }
            }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleTicks(context)
    }

    override fun onDisabled(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(clockTickPendingIntent(context))
        alarmManager.cancel(dataTickPendingIntent(context))
    }

    private fun refreshClockOnly(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        val now = LocalDateTime.now()
        views.setTextViewText(R.id.widget_clock_date, DATE_FORMATTER.format(now))
        views.setTextViewText(R.id.widget_clock_time, TIME_FORMATTER.format(now))
        appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
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

        // Drei getrennte Tap-Zonen: Uhrzeit/Datum/nächster Alarm -> Wecker, Wetter -> Wetter-Screen,
        // Termin-Agenda -> Kalender. Jede braucht eine eigene Request-Code-Nummer, da PendingIntent-
        // Gleichheit nur über Action/Component/Daten geprüft wird, nicht über die Extras - mit
        // identischem Request-Code würden sich die drei Intents gegenseitig überschreiben.
        val openWeckerPendingIntent = activityPendingIntent(context, requestCode = 1) {}
        val openWeatherPendingIntent = activityPendingIntent(context, requestCode = 2) {
            putExtra(EXTRA_OPEN_WEATHER, true)
        }
        val openCalendarPendingIntent = activityPendingIntent(context, requestCode = 3) {
            putExtra(EXTRA_OPEN_CALENDAR, true)
        }

        views.setOnClickPendingIntent(R.id.widget_clock_date, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_time, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_next_alarm_row, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_root, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_weather, openWeatherPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_agenda_row1, openCalendarPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_agenda_row2, openCalendarPendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun activityPendingIntent(
        context: Context,
        requestCode: Int,
        putExtras: Intent.() -> Unit
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply(putExtras)
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

            if (entry.type == AgendaEntryType.SHIFT) {
                // Eine eingetragene Schicht ist reine Hintergrundinfo und bekommt nur den
                // farbigen Punkt, damit ein tatsächlicher Termin mit Titel/Uhrzeit nicht durch
                // sie aus den wenigen sichtbaren Zeilen verdrängt wird.
                views.setViewVisibility(spec.textId, View.GONE)
            } else {
                views.setViewVisibility(spec.textId, View.VISIBLE)
                val isLastShown = index == agenda.entries.lastIndex
                val extraSuffix = if (isLastShown && agenda.extraCountForLastDay > 0) {
                    " (+${agenda.extraCountForLastDay})"
                } else {
                    ""
                }
                views.setTextViewText(spec.textId, formatAgendaText(entry, today) + extraSuffix)
            }
        }
    }

    private fun formatAgendaText(entry: AgendaEntry, today: LocalDate): String {
        val date = LocalDate.ofEpochDay(entry.epochDay)
        val dayLabel = if (date == today) "Heute" else AGENDA_WEEKDAY_FORMATTER.format(date)
        val timePart = entry.timeLabel?.let { ", $it" }.orEmpty()
        return "$dayLabel$timePart: ${entry.label}"
    }

    private fun scheduleTicks(context: Context) {
        scheduleNextClockTick(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val firstDataTriggerAt =
            (System.currentTimeMillis() / DATA_TICK_INTERVAL_MILLIS + 1) * DATA_TICK_INTERVAL_MILLIS
        alarmManager.setRepeating(
            AlarmManager.RTC, firstDataTriggerAt, DATA_TICK_INTERVAL_MILLIS, dataTickPendingIntent(context)
        )
    }

    /**
     * Einzelner, selbst-nachplanender exakter Alarm statt [AlarmManager.setRepeating]: Seit API 19
     * sind auch RTC-setRepeating-Alarme grundsätzlich ungenau (das System darf jede Auslösung
     * beliebig verschieben/batchen), und da jede Folge-Auslösung relativ zur tatsächlichen letzten
     * Auslösung statt zur nominalen Minutengrenze berechnet wird, summiert sich eine einmalige
     * Verzögerung über die Zeit immer weiter auf - sichtbar als zunehmender Rückstand der
     * angezeigten Uhrzeit. Jeder Tick plant hier stattdessen frisch gegen die nächste echte
     * Minutengrenze, wodurch sich eine einzelne Verzögerung nicht fortpflanzt.
     */
    private fun scheduleNextClockTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerAt =
            (System.currentTimeMillis() / CLOCK_TICK_INTERVAL_MILLIS + 1) * CLOCK_TICK_INTERVAL_MILLIS
        val pendingIntent = clockTickPendingIntent(context)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerAt, pendingIntent)
        }
    }

    private fun clockTickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockWidgetProvider::class.java).apply { action = ACTION_CLOCK_TICK }
        return PendingIntent.getBroadcast(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dataTickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockWidgetProvider::class.java).apply { action = ACTION_DATA_TICK }
        return PendingIntent.getBroadcast(
            context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
