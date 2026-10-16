package de.letheapp.nocturn.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Überträgt die per [WearAgendaBuilder] zusammengestellte Agenda über die Wear-Data-Layer-API
 * (Play Services) an eine gekoppelte WearOS-Smartwatch (siehe [de.letheapp.nocturn.wear.wear]-
 * seitiges Gegenstück `WearAgendaRepository` im `:wear`-Modul, das denselben Pfad liest). Läuft
 * periodisch über [WearSyncWorker] sowie sofort nach jeder relevanten Kalenderänderung (siehe
 * [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer.sync], das schon heute an genau den
 * Stellen aufgerufen wird, an denen sich Schichten/Rotationen ändern können) und direkt nach
 * jeder Umschaltung in der WearOS-Verwaltung ([WearSettingsViewModel]).
 *
 * Fehler (keine Uhr gekoppelt, Play Services nicht verfügbar) werden bewusst nur geloggt statt
 * geworfen — ein fehlgeschlagener Uhren-Sync darf nie die aufrufende Kalenderlogik stören.
 */
@Singleton
class WearSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agendaBuilder: WearAgendaBuilder
) {
    companion object {
        private const val TAG = "WearSyncManager"
        const val PATH_AGENDA = "/nocturn/agenda"
        const val KEY_GENERATED_AT = "generatedAtEpochMillis"
        const val KEY_ENTRIES = "entries"
        const val KEY_EPOCH_DAY = "epochDay"
        const val KEY_LABEL = "label"
        const val KEY_COLOR_ARGB = "colorArgb"
        const val KEY_TIME_LABEL = "timeLabel"
        const val KEY_TYPE = "type"
    }

    suspend fun syncNow() {
        runCatching {
            val entries = agendaBuilder.build()
            val request = PutDataMapRequest.create(PATH_AGENDA).apply {
                dataMap.putLong(KEY_GENERATED_AT, System.currentTimeMillis())
                dataMap.putDataMapArrayList(KEY_ENTRIES, ArrayList(entries.map { it.toDataMap() }))
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
        }.onFailure { e ->
            Log.w(TAG, "WearOS-Sync fehlgeschlagen (keine Uhr gekoppelt o. Ä.)", e)
        }
    }

    private fun WearAgendaEntry.toDataMap(): DataMap = DataMap().apply {
        putLong(KEY_EPOCH_DAY, epochDay)
        putString(KEY_LABEL, label)
        putInt(KEY_COLOR_ARGB, colorArgb)
        timeLabel?.let { putString(KEY_TIME_LABEL, it) }
        putString(KEY_TYPE, type.name)
    }
}
