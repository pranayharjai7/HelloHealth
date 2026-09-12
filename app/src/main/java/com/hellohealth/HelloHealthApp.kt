package com.hellohealth

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hellohealth.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Implements [Configuration.Provider] so WorkManager uses the [HiltWorkerFactory] and can therefore
 * construct @HiltWorker workers with injected dependencies (e.g. SyncWorker). This pairs with the
 * removal of the default `androidx.startup` WorkManagerInitializer in AndroidManifest.xml — without
 * that removal WorkManager would self-initialize with the default factory and @HiltWorker injection
 * would fail at runtime.
 *
 * Periodic sync is scheduled once here on create (KEEP), so the reconciler runs even if the user
 * never triggers a write.
 */
@HiltAndroidApp
class HelloHealthApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.ensurePeriodicSync()
    }
}
