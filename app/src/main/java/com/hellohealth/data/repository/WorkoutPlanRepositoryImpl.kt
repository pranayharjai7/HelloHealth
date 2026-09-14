package com.hellohealth.data.repository

import androidx.room.withTransaction
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.PlannedExerciseDao
import com.hellohealth.data.local.dao.WorkoutDayDao
import com.hellohealth.data.local.dao.WorkoutPlanDao
import com.hellohealth.data.local.entities.PlannedExerciseEntity
import com.hellohealth.data.local.entities.WorkoutDayEntity
import com.hellohealth.data.local.entities.WorkoutPlanEntity
import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.PlannedExercise
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.WorkoutDay
import com.hellohealth.domain.model.WorkoutPlan
import com.hellohealth.domain.repository.ExerciseRepository
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first repository owning the three planning tables (plans → days → planned exercises). Writes
 * hit Room with `isSynced=false` then poke [SyncScheduler]; the three syncers (Step 6) own the
 * Supabase round-trip. Reads are Room Flows, user-gated via [SupabaseSessionManager].
 *
 * With no signed-in user, reads emit empty/null and writes are dropped with a warning — matching
 * [EmotionsRepositoryImpl] so screens never special-case a missing session.
 *
 * Cascade delete ([deletePlan]/[deleteDay]) runs inside [AppDatabase.withTransaction] so parent and
 * all descendants tombstone atomically with one `nowMs` — a crash mid-cascade rolls back rather than
 * orphaning a tombstone. One [SyncScheduler.requestSync] is fired after the transaction commits.
 */
@Singleton
class WorkoutPlanRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val planDao: WorkoutPlanDao,
    private val dayDao: WorkoutDayDao,
    private val plannedDao: PlannedExerciseDao,
    private val exerciseRepository: ExerciseRepository,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
) : WorkoutPlanRepository {

    // ---------------------------------------------------------------- Plans

    override fun observePlans(): Flow<List<WorkoutPlan>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(planDao.observeForUser(userId).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override fun observeActivePlan(): Flow<WorkoutPlan?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        emitAll(planDao.observeActive(userId).map { it?.toDomain() })
    }.flowOn(Dispatchers.IO)

    override suspend fun getPlan(id: String): WorkoutPlan? = planDao.getById(id)?.toDomain()

    override suspend fun createPlan(name: String, planType: PlanType, makeActive: Boolean): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.WORKOUT_PLAN, "createPlan with no signed-in user; dropping write")
            return null
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        val id = UUID.randomUUID().toString()

        // Enforce at-most-one-active as a transaction: clear others, then insert active. Both writes
        // are unsynced tombstone-clock stamped so the syncer propagates the flip.
        db.withTransaction {
            if (makeActive) planDao.clearActiveForUser(userId, nowMs, tz)
            planDao.upsert(
                WorkoutPlanEntity(
                    id = id,
                    userId = userId,
                    name = name,
                    isActive = makeActive,
                    planType = planType.name,
                    createdAtEpochMs = nowMs,
                    updatedAtEpochMs = nowMs,
                    updatedAtTzOffsetMinutes = tz,
                    deletedAtEpochMs = null,
                    isSynced = false,
                )
            )
        }
        AppLogger.d(FeatureTag.WORKOUT_PLAN, "plan '$name' ($planType) created; requesting sync")
        syncScheduler.requestSync()
        return id
    }

    override suspend fun renamePlan(id: String, name: String) {
        val existing = planDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_PLAN, "renamePlan for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        planDao.upsert(
            existing.copy(
                name = name,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    override suspend fun setActivePlan(id: String) {
        val existing = planDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_PLAN, "setActivePlan for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        db.withTransaction {
            planDao.clearActiveForUser(existing.userId, nowMs, tz)
            planDao.upsert(
                existing.copy(
                    isActive = true,
                    updatedAtEpochMs = nowMs,
                    updatedAtTzOffsetMinutes = tz,
                    isSynced = false,
                )
            )
        }
        syncScheduler.requestSync()
    }

    override suspend fun deletePlan(id: String) {
        val existing = planDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_PLAN, "deletePlan for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        // Leaf → middle → parent, one clock, atomic. Order within the tx is irrelevant to correctness
        // (id-based rows + deletedAtEpochMs read filter), but leaf-first keeps intermediate reads sane.
        db.withTransaction {
            plannedDao.tombstoneForPlan(id, nowMs, tz)
            dayDao.tombstoneForPlan(id, nowMs, tz)
            planDao.tombstone(id, nowMs, tz)
        }
        AppLogger.d(FeatureTag.WORKOUT_PLAN, "plan id=$id + descendants tombstoned; requesting sync")
        syncScheduler.requestSync()
    }

    // ---------------------------------------------------------------- Days

    override fun observeDays(planId: String): Flow<List<WorkoutDay>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(dayDao.observeForPlan(planId).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override suspend fun getDay(id: String): WorkoutDay? = dayDao.getById(id)?.toDomain()

    override suspend fun addDay(planId: String, slotKey: String, name: String): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.WORKOUT_DAY, "addDay with no signed-in user; dropping write")
            return null
        }
        val nowMs = Timestamps.nowEpochMs()
        val id = UUID.randomUUID().toString()
        dayDao.upsert(
            WorkoutDayEntity(
                id = id,
                planId = planId,
                userId = userId,
                slotKey = slotKey,
                name = name,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false,
            )
        )
        AppLogger.d(FeatureTag.WORKOUT_DAY, "day '$name' ($slotKey) added to plan=$planId; requesting sync")
        syncScheduler.requestSync()
        return id
    }

    override suspend fun renameDay(id: String, name: String) {
        val existing = dayDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_DAY, "renameDay for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        dayDao.upsert(
            existing.copy(
                name = name,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    override suspend fun deleteDay(id: String) {
        val existing = dayDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_DAY, "deleteDay for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        db.withTransaction {
            plannedDao.tombstoneForDay(id, nowMs, tz)
            dayDao.tombstone(id, nowMs, tz)
        }
        AppLogger.d(FeatureTag.WORKOUT_DAY, "day id=$id + planned exercises tombstoned; requesting sync")
        syncScheduler.requestSync()
    }

    // ---------------------------------------------------- Planned exercises

    override fun observePlannedExercises(dayId: String): Flow<List<PlannedExerciseWithDetails>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            plannedDao.observeForDay(dayId).map { rows ->
                // One catalog batch lookup per emission; a missing catalog entry → exercise=null
                // (row still shown with a graceful fallback rather than dropped).
                val byId = exerciseRepository.getByIds(rows.map { it.exerciseId })
                rows.map { entity ->
                    val planned = entity.toDomain()
                    PlannedExerciseWithDetails(planned = planned, exercise = byId[planned.exerciseId])
                }
            }
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun getPlannedExercise(id: String): PlannedExerciseWithDetails? {
        val entity = plannedDao.getById(id)?.takeIf { it.deletedAtEpochMs == null } ?: return null
        val planned = entity.toDomain()
        val exercise = exerciseRepository.getById(planned.exerciseId)
        return PlannedExerciseWithDetails(planned = planned, exercise = exercise)
    }

    override suspend fun addExercise(dayId: String, exerciseId: String): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.PLANNED_EXERCISE, "addExercise with no signed-in user; dropping write")
            return null
        }
        val nowMs = Timestamps.nowEpochMs()
        val id = UUID.randomUUID().toString()
        val nextOrder = (plannedDao.maxOrderIndexForDay(dayId) ?: -1) + 1
        plannedDao.upsert(
            PlannedExerciseEntity(
                id = id,
                dayId = dayId,
                userId = userId,
                exerciseId = exerciseId,
                orderIndex = nextOrder,
                targetSets = DEFAULT_TARGET_SETS,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false,
            )
        )
        AppLogger.d(FeatureTag.PLANNED_EXERCISE, "exercise=$exerciseId added to day=$dayId; requesting sync")
        syncScheduler.requestSync()
        return id
    }

    override suspend fun updateTargets(planned: PlannedExercise) {
        val existing = plannedDao.getById(planned.id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.PLANNED_EXERCISE, "updateTargets for unknown/deleted id=${planned.id}; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        plannedDao.upsert(
            existing.copy(
                targetSets = planned.targetSets,
                targetReps = planned.targetReps,
                targetWeightKg = planned.targetWeightKg,
                targetDurationSeconds = planned.targetDurationSeconds,
                targetDistanceKm = planned.targetDistanceKm,
                targetSpeedKmh = planned.targetSpeedKmh,
                targetIncline = planned.targetIncline,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    override suspend fun reorderExercises(dayId: String, orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                val existing = plannedDao.getById(id) ?: return@forEachIndexed
                if (existing.deletedAtEpochMs != null || existing.dayId != dayId) return@forEachIndexed
                if (existing.orderIndex == index) return@forEachIndexed // no-op: don't churn sync state
                plannedDao.upsert(
                    existing.copy(
                        orderIndex = index,
                        updatedAtEpochMs = nowMs,
                        updatedAtTzOffsetMinutes = tz,
                        isSynced = false,
                    )
                )
            }
        }
        syncScheduler.requestSync()
    }

    override suspend fun deleteExercise(id: String) {
        val existing = plannedDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.PLANNED_EXERCISE, "deleteExercise for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        plannedDao.tombstone(id, nowMs, Timestamps.currentTzOffsetMinutes())
        AppLogger.d(FeatureTag.PLANNED_EXERCISE, "planned exercise id=$id tombstoned; requesting sync")
        syncScheduler.requestSync()
    }

    // ---------------------------------------------------------------- Mappers

    private fun WorkoutPlanEntity.toDomain() = WorkoutPlan(
        id = id,
        userId = userId,
        name = name,
        isActive = isActive,
        planType = PlanType.fromName(planType),
        createdAt = createdAtEpochMs,
        updatedAt = updatedAtEpochMs,
    )

    private fun WorkoutDayEntity.toDomain() = WorkoutDay(
        id = id,
        planId = planId,
        userId = userId,
        slotKey = slotKey,
        name = name,
        updatedAt = updatedAtEpochMs,
    )

    private fun PlannedExerciseEntity.toDomain() = PlannedExercise(
        id = id,
        dayId = dayId,
        userId = userId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        updatedAt = updatedAtEpochMs,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = targetWeightKg,
        targetDurationSeconds = targetDurationSeconds,
        targetDistanceKm = targetDistanceKm,
        targetSpeedKmh = targetSpeedKmh,
        targetIncline = targetIncline,
    )

    private companion object {
        const val DEFAULT_TARGET_SETS = 3
    }
}
