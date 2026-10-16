package de.letheapp.nocturn.watch

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Pfad/Schlüssel müssen exakt zu `de.letheapp.nocturn.wear.WearSyncManager` im Handy-Modul (`:app`) passen. */
private const val PATH_AGENDA = "/nocturn/agenda"
private const val KEY_GENERATED_AT = "generatedAtEpochMillis"
private const val KEY_ENTRIES = "entries"
private const val KEY_EPOCH_DAY = "epochDay"
private const val KEY_LABEL = "label"
private const val KEY_COLOR_ARGB = "colorArgb"
private const val KEY_TIME_LABEL = "timeLabel"
private const val KEY_TYPE = "type"

enum class WatchAgendaEntryType { SHIFT, BIRTHDAY, PUBLIC_HOLIDAY, SCHOOL_HOLIDAY, UNKNOWN }

data class WatchAgendaEntry(
    val epochDay: Long,
    val label: String,
    val colorArgb: Int,
    val timeLabel: String?,
    val type: WatchAgendaEntryType
)

data class WatchAgenda(
    val generatedAtEpochMillis: Long?,
    val entries: List<WatchAgendaEntry>
)

/**
 * Liest die vom Handy übertragene Kalenderagenda über die Wear-Data-Layer-API. Beim Start wird der
 * zuletzt übertragene Stand direkt per [DataClient.getDataItems] abgefragt (funktioniert auch ohne
 * gerade erreichbares Handy, da die Data-Layer-API den letzten Stand lokal auf der Uhr hält),
 * danach hält [observeAgenda] über [DataClient.addListener] live nach.
 */
class WearAgendaRepository(private val context: Context) {

    suspend fun loadCurrent(): WatchAgenda? {
        val items = Wearable.getDataClient(context).dataItems.await()
        try {
            for (i in 0 until items.count) {
                val item = items[i]
                if (item.uri.path == PATH_AGENDA) {
                    return DataMapItem.fromDataItem(item).dataMap.toWatchAgenda()
                }
            }
        } finally {
            items.release()
        }
        return null
    }

    fun observeAgenda(): Flow<WatchAgenda> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH_AGENDA) {
                    trySend(DataMapItem.fromDataItem(event.dataItem).dataMap.toWatchAgenda())
                }
            }
        }
        Wearable.getDataClient(context).addListener(listener)
        awaitClose { Wearable.getDataClient(context).removeListener(listener) }
    }
}

private fun DataMap.toWatchAgenda(): WatchAgenda {
    val generatedAt = if (containsKey(KEY_GENERATED_AT)) getLong(KEY_GENERATED_AT) else null
    val entries = getDataMapArrayList(KEY_ENTRIES).orEmpty().map { it.toWatchAgendaEntry() }
    return WatchAgenda(generatedAt, entries)
}

private fun DataMap.toWatchAgendaEntry(): WatchAgendaEntry = WatchAgendaEntry(
    epochDay = getLong(KEY_EPOCH_DAY),
    label = getString(KEY_LABEL).orEmpty(),
    colorArgb = getInt(KEY_COLOR_ARGB),
    timeLabel = getString(KEY_TIME_LABEL),
    type = runCatching { WatchAgendaEntryType.valueOf(getString(KEY_TYPE).orEmpty()) }
        .getOrDefault(WatchAgendaEntryType.UNKNOWN)
)
