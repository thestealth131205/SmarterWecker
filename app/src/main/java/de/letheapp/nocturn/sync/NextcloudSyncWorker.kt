package de.letheapp.nocturn.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodischer Hintergrund-Sync mit Nextcloud (siehe [NextcloudSyncRepository]), alle paar Stunden über WorkManager angestoßen. */
@HiltWorker
class NextcloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: NextcloudSyncRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = syncRepository.syncNow()
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "nextcloud_sync"
    }
}
