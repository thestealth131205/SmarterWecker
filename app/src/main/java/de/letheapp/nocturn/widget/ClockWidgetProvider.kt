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
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ACTION_TICK = "de.letheapp.nocturn.widget.ACTION_CLOCK_TICK"
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val NEXT_ALARM_FORMATTER = DateTimeFormatter.ofPattern("EEE, HH:mm", Locale.GERMAN)

/**
 * Uhr-Widget (CLAUDE.md Kernfunktion 7): Datum/Uhrzeit/nächster Alarm, transparent über dem
 * Homescreen-Hintergrund. "Nächster Alarm" kommt direkt von [AlarmManager.getNextAlarmClock]
 * statt aus der eigenen DB, weil [de.letheapp.nocturn.alarm.AlarmScheduler] Alarme bereits über
 * `setAlarmClock` plant — das System kennt die nächste Weckzeit also ohnehin schon.
 *
 * Da offizielle Widget-Updates (`updatePeriodMillis`) minütlich nicht zuverlässig genug sind,
 * stößt sich das Widget selbst per [AlarmManager.setRepeating] jede Minute neu an.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        scheduleTicks(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            ids.forEach { updateWidget(context, appWidgetManager, it) }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleTicks(context)
    }

    override fun onDisabled(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(tickPendingIntent(context))
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
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

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_clock_root, openAppPendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun scheduleTicks(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val firstTriggerAt = (System.currentTimeMillis() / 60_000L + 1) * 60_000L
        alarmManager.setRepeating(AlarmManager.RTC, firstTriggerAt, AlarmManager.INTERVAL_MINUTE, tickPendingIntent(context))
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockWidgetProvider::class.java).apply { action = ACTION_TICK }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
