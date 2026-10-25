package de.letheapp.nocturn.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.app.WallpaperColors
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
private const val ACTION_OPEN_WECKER = "de.letheapp.nocturn.widget.ACTION_CLOCK_OPEN_WECKER"
private const val ACTION_OPEN_WEATHER = "de.letheapp.nocturn.widget.ACTION_CLOCK_OPEN_WEATHER"
private const val ACTION_OPEN_CALENDAR = "de.letheapp.nocturn.widget.ACTION_CLOCK_OPEN_CALENDAR"
private const val CLOCK_TICK_INTERVAL_MILLIS = 60_000L
private const val DATA_TICK_INTERVAL_MILLIS = 20 * 60_000L
private const val AGENDA_SHOWN_COUNT = 2
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val NEXT_ALARM_FORMATTER = DateTimeFormatter.ofPattern("EEE, HH:mm", Locale.GERMAN)
private val AGENDA_WEEKDAY_FORMATTER = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)

// Weißer Text (Default) für dunkle/dunkel wirkende Wallpaper, dunkler Text, sobald
// WallpaperColors.HINT_SUPPORTS_DARK_TEXT meldet, dass der Hintergrund hell genug dafür ist.
private const val TEXT_PRIMARY_ON_DARK = 0xFFFFFFFF.toInt()
private const val TEXT_SECONDARY_ON_DARK = 0xCCFFFFFF.toInt()
private const val TEXT_PRIMARY_ON_LIGHT = 0xE6101010.toInt()
private const val TEXT_SECONDARY_ON_LIGHT = 0xB3101010.toInt()

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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ClockWidgetConfigureActivity.clearSelection(context, it) }
    }

    private fun refreshClockOnly(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val alignment = ClockWidgetConfigureActivity.alignmentFor(context, widgetId)
        val views = RemoteViews(context.packageName, layoutResIdFor(alignment))
        val now = LocalDateTime.now()
        views.setTextViewText(R.id.widget_clock_date, DATE_FORMATTER.format(now))
        views.setTextViewText(R.id.widget_clock_time, TIME_FORMATTER.format(now))
        applyTextColors(context, views)
        appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
    }

    private fun refreshWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val locationId = ClockWidgetConfigureActivity.locationIdFor(context, widgetId)
                val alignment = ClockWidgetConfigureActivity.alignmentFor(context, widgetId)
                val weather = runCatching { weatherRepository.currentWeather(locationId) }.getOrNull()
                val agenda = runCatching { calendarRepository.getUpcomingAgendaForWidget(AGENDA_SHOWN_COUNT) }
                    .getOrNull() ?: WidgetAgenda(emptyList(), 0)
                updateWidget(context, appWidgetManager, widgetId, alignment, weather?.let { "${it.emoji} ${it.temperatureCelsius}°" }, agenda)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        alignment: WidgetAlignment,
        weatherLabel: String?,
        agenda: WidgetAgenda
    ) {
        val views = RemoteViews(context.packageName, layoutResIdFor(alignment))
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
        applyTextColors(context, views)

        // Drei getrennte Tap-Zonen: Uhrzeit/Datum/nächster Alarm -> Wecker, Wetter -> Wetter-Screen,
        // Termin-Agenda -> Kalender. PendingIntent-Gleichheit wird über Action/Component/Daten
        // geprüft, NICHT über die Extras oder den Request-Code alleine (der Request-Code ist nur
        // ein zusätzliches Unterscheidungsmerkmal, wenn Action/Component/Daten bereits gleich sind).
        // Jede der drei Intents bekommt daher zusätzlich eine eigene, eindeutige Action-Zeichenkette
        // - sonst wäre sie mit dem bare "Intent(context, MainActivity::class.java)" von
        // CalendarWidgetProvider identisch, und FLAG_UPDATE_CURRENT hätte dort live die Extras
        // überschrieben (beobachteter Bug: Tippen auf die Uhr öffnete den Kalender).
        val openWeckerPendingIntent = activityPendingIntent(context, requestCode = 1) {
            action = ACTION_OPEN_WECKER
        }
        val openWeatherPendingIntent = activityPendingIntent(context, requestCode = 2) {
            action = ACTION_OPEN_WEATHER
            putExtra(EXTRA_OPEN_WEATHER, true)
        }
        val openCalendarPendingIntent = activityPendingIntent(context, requestCode = 3) {
            action = ACTION_OPEN_CALENDAR
            putExtra(EXTRA_OPEN_CALENDAR, true)
        }

        views.setOnClickPendingIntent(R.id.widget_clock_date, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_time, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_next_alarm_row, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_root, openWeckerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_weather, openWeatherPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_clock_shift_dots_row, openCalendarPendingIntent)
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

    private fun layoutResIdFor(alignment: WidgetAlignment): Int = when (alignment) {
        WidgetAlignment.LEFT -> R.layout.widget_clock_left
        WidgetAlignment.RIGHT -> R.layout.widget_clock_right
        WidgetAlignment.CENTER -> R.layout.widget_clock
    }

    /**
     * Sind die beiden gezeigten Einträge BEIDE Schichten (z. B. mehrere Schichten am selben Tag,
     * siehe CLAUDE.md Kernfunktion 6), werden sie nebeneinander in einer gemeinsamen Punkte-Zeile
     * dargestellt statt wie zuvor als zwei separate, untereinander gestapelte Zeilen - sonst
     * nähmen zwei reine Hintergrund-Punkte unnötig doppelt so viel vertikalen Platz ein wie ein
     * einzelner Termin mit Titel/Uhrzeit.
     */
    private fun applyAgenda(views: RemoteViews, agenda: WidgetAgenda) {
        val today = LocalDate.now()
        val entries = agenda.entries
        val bothShift = entries.size >= 2 &&
            entries[0].type == AgendaEntryType.SHIFT &&
            entries[1].type == AgendaEntryType.SHIFT

        if (bothShift) {
            views.setViewVisibility(R.id.widget_clock_shift_dots_row, View.VISIBLE)
            views.setInt(R.id.widget_clock_shift_dot_1, "setColorFilter", entries[0].colorArgb)
            views.setInt(R.id.widget_clock_shift_dot_2, "setColorFilter", entries[1].colorArgb)
            AGENDA_ROW_SPECS.forEach { spec -> views.setViewVisibility(spec.rowId, View.GONE) }
            return
        }

        views.setViewVisibility(R.id.widget_clock_shift_dots_row, View.GONE)
        AGENDA_ROW_SPECS.forEachIndexed { index, spec ->
            val entry = entries.getOrNull(index)
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
                val isLastShown = index == entries.lastIndex
                val extraSuffix = if (isLastShown && agenda.extraCountForLastDay > 0) {
                    " (+${agenda.extraCountForLastDay})"
                } else {
                    ""
                }
                views.setTextViewText(spec.textId, formatAgendaText(entry, today) + extraSuffix)
            }
        }
    }

    /**
     * Passt die Textfarbe des (transparent über dem Homescreen-Hintergrund liegenden) Uhr-Widgets
     * an die tatsächliche Wallpaper-Helligkeit an, statt stur Weiß zu nutzen - bei einem hellen
     * Hintergrundbild wäre weißer Text sonst kaum lesbar. [WallpaperManager.getWallpaperColors]
     * liefert dafür ohne zusätzliche Berechtigung einen [WallpaperColors.HINT_SUPPORTS_DARK_TEXT]-
     * Hinweis (dieselbe API, über die z. B. Launcher die Lesbarkeit von App-Labels auf dem
     * Homescreen entscheiden).
     */
    private fun applyTextColors(context: Context, views: RemoteViews) {
        val lightBackground = isLightWallpaper(context)
        val primary = if (lightBackground) TEXT_PRIMARY_ON_LIGHT else TEXT_PRIMARY_ON_DARK
        val secondary = if (lightBackground) TEXT_SECONDARY_ON_LIGHT else TEXT_SECONDARY_ON_DARK
        views.setTextColor(R.id.widget_clock_date, secondary)
        views.setTextColor(R.id.widget_clock_time, primary)
        views.setTextColor(R.id.widget_clock_weather, secondary)
        views.setTextColor(R.id.widget_clock_next_alarm, secondary)
        views.setTextColor(R.id.widget_clock_agenda_text1, secondary)
        views.setTextColor(R.id.widget_clock_agenda_text2, secondary)
        views.setInt(R.id.widget_clock_next_alarm_icon, "setColorFilter", secondary)
    }

    private fun isLightWallpaper(context: Context): Boolean {
        return try {
            val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        } catch (e: Exception) {
            false
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
