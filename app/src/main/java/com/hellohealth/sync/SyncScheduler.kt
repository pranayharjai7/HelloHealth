package com.hellohealth.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns WorkManager scheduling for the offline-first sync reconciler. Two triggers:
 *
 *  - [ensurePeriodicSync]: a ~15-min periodic pass (min WorkManager interval), CONNECTED-only,
 *    enqueued with KEEP so app relaunches don't reset its cadence. Guarantees eventual sync.
 *  - [requestSync]: a one-shot expedited pass fired after each local write, so an online user
 *    sees their change reach Supabase near-immediately. Unique + REPLACE collapses bursts of
 *    edits into a single pending run.
 *
 * Repositories depend only on [requestSync]; periodic scheduling is owned by the Application.
 */
@Singleton
open class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val connectedConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Enqueue the periodic reconciler once; safe to call on every app start (KEEP). */
    fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(connectedConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        AppLogger.d(FeatureTag.SYNC, "Periodic sync ensured (interval=${PERIODIC_INTERVAL_MINUTES}m)")
    }

    /** Fire an expedited one-shot sync after a local write. Bursts collapse into one pending run. */
    open fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connectedConstraint)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            ONE_SHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        AppLogger.d(FeatureTag.SYNC, "One-shot sync requested")
    }

    companion object {
        const val PERIODIC_WORK_NAME = "hh.sync.periodic"
        const val ONE_SHOT_WORK_NAME = "hh.sync.oneshot"
        const val PERIODIC_INTERVAL_MINUTES = 15L
    }
}
