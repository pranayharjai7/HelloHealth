package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.SyncLogDao
import com.hellohealth.data.local.entities.SyncLogEntity
import com.hellohealth.data.repository.SupabaseSessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs every bound [Syncer] each sync pass. For each syncer: **push before pull** (local edits win
 * exact-millis ties), aggregate counts, and write one `sync_log` row. One syncer's failure is
 * isolated — the others still run — but if ANY syncer failed the whole pass reports
 * [Result.hadFailure] so the worker returns `retry()` and the failed rows (still `isSynced=false`)
 * are re-attempted. Nothing is ever dropped.
 */
@Singleton
class SyncOrchestrator @Inject constructor(
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
    private val sessionManager: SupabaseSessionManager,
    private val syncLogDao: SyncLogDao
) {
    /** Aggregate result of one full pass. */
    data class Result(val ranForUser: Boolean, val hadFailure: Boolean)

    suspend fun syncAll(): Result {
        val userId = runCatching { sessionManager.getCurrentUserId() }.getOrNull()
        if (userId == null) {
            AppLogger.d(FeatureTag.SYNC, "syncAll: no signed-in user; nothing to do")
            return Result(ranForUser = false, hadFailure = false)
        }

        var anyFailure = false
        for (syncer in syncers) {
            val startedAt = Timestamps.nowEpochMs()
            var pushed = 0
            var pulled = 0
            var failures = 0
            var resultLabel = "ok"

            try {
                pushed = syncer.push(userId)
                pulled = syncer.pull(userId)
            } catch (e: Exception) {
                failures = 1
                anyFailure = true
                resultLabel = "failed: ${e.javaClass.simpleName}"
                AppLogger.e(FeatureTag.SYNC, "Syncer ${syncer.featureTag.tag} failed", e)
            }

            syncLogDao.insert(
                SyncLogEntity(
                    runAtEpochMs = startedAt,
                    featureTag = syncer.featureTag.tag,
                    pushed = pushed,
                    pulled = pulled,
                    conflicts = 0,
                    failures = failures,
                    durationMs = Timestamps.nowEpochMs() - startedAt,
                    resultLabel = resultLabel
                )
            )
            AppLogger.d(
                FeatureTag.SYNC,
                "${syncer.featureTag.tag}: pushed=$pushed pulled=$pulled failures=$failures"
            )
        }

        syncLogDao.trimTo(MAX_LOG_ROWS)
        return Result(ranForUser = true, hadFailure = anyFailure)
    }

    companion object {
        private const val MAX_LOG_ROWS = 200
    }
}
