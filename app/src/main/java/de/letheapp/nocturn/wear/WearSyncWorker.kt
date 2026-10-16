package de.letheapp.nocturn.wear

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodischer Hintergrund-Sync der Kalenderagenda zu einer gekoppelten WearOS-Smartwatch (siehe
 * [WearSyncManager]) — Sicherheitsnetz für den Fall, dass die unmittelbaren Sync-Aufrufe nach
 * Kalenderänderungen die App gerade nicht im Vordergrund treffen (Prozess war beendet). Läuft
 * alle 2 Stunden, analog zum bestehenden [de.letheapp.nocturn.sync.NextcloudSyncWorker] (dort 4h).
 */
@HiltWorker
class WearSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val wearSyncManager: WearSyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        wearSyncManager.syncNow()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "wear_sync"
    }
}
