package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.data.local.entities.GoalsEntity
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first goals repository. Room is the source of truth the UI reads; writes hit Room
 * (`isSynced=false`) and then poke [SyncScheduler] to reconcile with Supabase in the background.
 * The [GoalsSyncer] handles the actual push/pull, so this class no longer touches Postgrest.
 *
 * When there is no signed-in user or no stored row, reads fall back to [ActivityGoals] defaults —
 * matching the previous behavior so no screen changes.
 */
@Singleton
class GoalsRepositoryImpl @Inject constructor(
    private val goalsDao: GoalsDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler
) : GoalsRepository {

    override fun getActivityGoals(): Flow<ActivityGoals> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(ActivityGoals())
            return@flow
        }
        emitAll(goalsDao.observe(userId).map { it?.toDomain() ?: ActivityGoals() })
    }.flowOn(Dispatchers.IO)

    override suspend fun getCurrentActivityGoals(): ActivityGoals {
        val userId = sessionManager.getCurrentUserId() ?: return ActivityGoals()
        return goalsDao.get(userId)?.toDomain() ?: ActivityGoals()
    }

    override suspend fun updateActivityGoals(goals: ActivityGoals) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.GOALS, "updateActivityGoals with no signed-in user; dropping write")
            return
        }

        goalsDao.upsert(
            GoalsEntity(
                userId = userId,
                steps = goals.steps,
                activeCalories = goals.activeCalories,
                activeMinutes = goals.activeMinutes,
                updatedAtEpochMs = Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.GOALS, "goals written locally; requesting sync")
        syncScheduler.requestSync()
    }

    private fun GoalsEntity.toDomain() = ActivityGoals(
        steps = steps,
        activeCalories = activeCalories,
        activeMinutes = activeMinutes
    )
}
