package com.hellohealth.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.SyncLogDao
import com.hellohealth.data.local.entities.SyncLogEntity
import com.hellohealth.data.repository.SupabaseSessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background reconciler for the offline-first sync spine. Scheduled by [SyncScheduler]
 * (periodic + expedited-on-write).
 *
 * Step 4 skeleton: it only proves the WorkManager + Hilt plumbing end-to-end — verifies a user is
 * signed in, then writes a single `sync_log` row and returns success. The real push/pull is wired
 * in Step 5 when [SyncOrchestrator] and the per-table Syncers land.
 *
 * No-user is a clean no-op ([success], never [retry]) so a logged-out or mid-init app never spins a
 * retry loop.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessionManager: SupabaseSessionManager,
    private val syncLogDao: SyncLogDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val startedAt = Timestamps.nowEpochMs()

        val userId = runCatching { sessionManager.getCurrentUserId() }
            .getOrElse { e ->
                AppLogger.e(FeatureTag.SYNC, "Failed to resolve current user; skipping sync run", e)
                null
            }

        if (userId == null) {
            // Logged out or session not ready — nothing to sync. Do NOT retry (avoids a loop).
            AppLogger.d(FeatureTag.SYNC, "No signed-in user; sync run is a no-op")
            return Result.success()
        }

        return try {
            // Step 4 placeholder: orchestration lands in Step 5. Record that the worker ran.
            syncLogDao.insert(
                SyncLogEntity(
                    runAtEpochMs = startedAt,
                    featureTag = FeatureTag.SYNC.tag,
                    pushed = 0,
                    pulled = 0,
                    conflicts = 0,
                    failures = 0,
                    durationMs = Timestamps.nowEpochMs() - startedAt,
                    resultLabel = "skeleton-noop"
                )
            )
            syncLogDao.trimTo(MAX_LOG_ROWS)
            AppLogger.d(FeatureTag.SYNC, "Sync worker ran (skeleton) for user=$userId")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(FeatureTag.SYNC, "Sync worker failed; will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val MAX_LOG_ROWS = 200
    }
}
