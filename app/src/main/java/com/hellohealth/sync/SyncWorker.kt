package com.hellohealth.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background reconciler for the offline-first sync spine. Scheduled by [SyncScheduler]
 * (periodic + expedited-on-write) and delegates the actual push/pull to [SyncOrchestrator].
 *
 * Result mapping:
 *  - no signed-in user (or nothing ran) -> [success], never [retry] (avoids a logged-out loop)
 *  - a syncer failed -> [retry], so the still-`isSynced=false` rows are re-attempted with backoff
 *  - an unexpected throw -> [retry]
 * Data is never dropped: a failed push leaves the row unsynced for the next pass.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: SyncOrchestrator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = orchestrator.syncAll()
            when {
                !result.ranForUser -> Result.success()
                result.hadFailure -> {
                    AppLogger.d(FeatureTag.SYNC, "Sync had failures; scheduling retry")
                    Result.retry()
                }
                else -> Result.success()
            }
        } catch (e: Exception) {
            AppLogger.e(FeatureTag.SYNC, "Sync worker crashed; scheduling retry", e)
            Result.retry()
        }
    }
}
