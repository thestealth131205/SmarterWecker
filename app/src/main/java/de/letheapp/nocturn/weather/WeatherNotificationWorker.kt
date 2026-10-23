package de.letheapp.nocturn.weather

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.letheapp.nocturn.EXTRA_OPEN_WEATHER
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.onboarding.AppPreferences
import java.time.ZonedDateTime
import kotlin.math.ceil

const val CHANNEL_WEATHER = "weather_notifications"
private const val ACTION_OPEN_WEATHER_FROM_NOTIFICATION = "de.letheapp.nocturn.weather.ACTION_OPEN_WEATHER"
private const val NOTIFICATION_ID_RAIN = 4001
private const val NOTIFICATION_ID_MORNING = 4002
private const val RAIN_POP_THRESHOLD = 50
private val MORNING_WINDOW_HOURS = 6..10
private val RAINY_EMOJIS = setOf("🌧", "⛈", "🌦")

/**
 * Periodischer Hintergrund-Check (alle 15 Minuten, das Minimum für
 * [androidx.work.PeriodicWorkRequest]) für zwei Wetter-Benachrichtigungen, nur solange
 * [AppPreferences.isWeatherEnabled] aktiv ist:
 *
 * 1. **Regen-Vorwarnung**: Ist es aktuell trocken, aber laut stündlicher Vorhersage demnächst
 *    Regen angesagt (Regenwahrscheinlichkeit ≥ [RAIN_POP_THRESHOLD] %), wird einmalig pro
 *    erkannter Regen-Stunde benachrichtigt (Dedup über [AppPreferences.getLastRainNotifiedHour]).
 * 2. **Morgen-Übersicht**: Einmal pro Tag im Fenster [MORNING_WINDOW_HOURS] (Dedup über
 *    [AppPreferences.getLastMorningNotifiedDay]) mit der aktuellen gefühlten Temperatur, dem
 *    Wettersymbol für den heutigen Tag sowie Kälte-/Windhinweis.
 */
@HiltWorker
class WeatherNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!appPreferences.isWeatherEnabled()) return Result.success()

        val weather = weatherRepository.currentWeather() ?: return Result.success()
        val hourly = weatherRepository.hourlyForecastToday().orEmpty()
        val forecast = weatherRepository.dailyForecast().orEmpty()

        checkRainNotification(weather, hourly)
        checkMorningNotification(weather, forecast)

        return Result.success()
    }

    private fun checkRainNotification(weather: DetailedWeather, hourly: List<HourlyForecast>) {
        if (weather.emoji in RAINY_EMOJIS) return // schon nass, keine Vorwarnung nötig

        val now = System.currentTimeMillis()
        val upcomingRain = hourly
            .filter { it.timeEpochMillis > now }
            .firstOrNull { it.precipitationProbabilityPercent >= RAIN_POP_THRESHOLD && it.emoji in RAINY_EMOJIS }
            ?: return

        if (appPreferences.getLastRainNotifiedHour() == upcomingRain.timeEpochMillis) return

        val hoursUntil = ceil((upcomingRain.timeEpochMillis - now) / 3_600_000.0).toInt().coerceAtLeast(1)
        showNotification(
            id = NOTIFICATION_ID_RAIN,
            title = "${upcomingRain.emoji} Regen angesagt",
            text = "In den nächsten $hoursUntil Stunden ist Regen angesagt."
        )
        appPreferences.setLastRainNotifiedHour(upcomingRain.timeEpochMillis)
    }

    private fun checkMorningNotification(weather: DetailedWeather, forecast: List<DailyForecast>) {
        val now = ZonedDateTime.now()
        if (now.hour !in MORNING_WINDOW_HOURS) return

        val today = now.toLocalDate().toEpochDay()
        if (appPreferences.getLastMorningNotifiedDay() == today) return

        val emoji = forecast.firstOrNull()?.emoji ?: weather.emoji
        val coldSuffix = if (weather.feelsLikeCelsius < 12) " Es ist kalt." else ""
        val windySuffix = if (weather.windSpeedKmh > 10) " Es wird windig." else ""
        val text = "Jetzt sind es gerade gefühlt ${weather.feelsLikeCelsius}°.$coldSuffix$windySuffix"

        showNotification(
            id = NOTIFICATION_ID_MORNING,
            title = "$emoji Wetter heute",
            text = text
        )
        appPreferences.setLastMorningNotifiedDay(today)
    }

    private fun showNotification(id: Int, title: String, text: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_WEATHER, "Wetter-Hinweise", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            Intent(applicationContext, MainActivity::class.java).apply {
                action = ACTION_OPEN_WEATHER_FROM_NOTIFICATION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_WEATHER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_WEATHER)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    companion object {
        const val WORK_NAME = "weather_notifications"
    }
}
