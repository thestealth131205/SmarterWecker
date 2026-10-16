package de.letheapp.nocturn

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import de.letheapp.nocturn.sync.NextcloudSyncWorker
import de.letheapp.nocturn.wear.WearSyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NocturnApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Periodischer Hintergrund-Sync mit Nextcloud (siehe CLAUDE.md "Kalendersync"), läuft nur,
        // wenn tatsächlich ein Konto verbunden ist (siehe NextcloudSyncRepository.syncNow).
        val request = PeriodicWorkRequestBuilder<NextcloudSyncWorker>(4, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NextcloudSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // Sicherheitsnetz-Sync zur gekoppelten WearOS-Smartwatch (siehe WearSyncManager) — die
        // eigentlich maßgeblichen Syncs laufen sofort nach Kalenderänderungen bzw. nach einer
        // Umschaltung in der WearOS-Verwaltung, dieser periodische Lauf fängt nur verpasste Fälle ab.
        val wearRequest = PeriodicWorkRequestBuilder<WearSyncWorker>(2, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WearSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            wearRequest
        )
    }
}
