package com.hellohealth.data.repository

import androidx.room.withTransaction
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.SessionSetDao
import com.hellohealth.data.local.dao.WorkoutSessionDao
import com.hellohealth.data.local.entities.SessionSetEntity
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import com.hellohealth.domain.model.SessionSet
import com.hellohealth.domain.model.SessionStatus
import com.hellohealth.domain.model.WorkoutSession
import com.hellohealth.domain.repository.WorkoutSessionRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first repository owning the two live-logging tables (sessions → sets). Writes hit Room with
 * `isSynced=false` then poke [SyncScheduler]; [com.hellohealth.sync.WorkoutSessionSyncer] /
 * [com.hellohealth.sync.SessionSetSyncer] own the Supabase round-trip. Reads are Room Flows, gated
 * on a signed-in user (empty/null when signed out, matching [WorkoutPlanRepositoryImpl]).
 *
 * Single-active-session invariant: [startSession] abandons any currently-active session in the same
 * transaction that opens the new one, so there is never a window with two active sessions.
 *
 * [finishSession] recomputes [WorkoutSession.totalVolumeKg] from the session's live sets.
 * [deleteSession] cascades to all its sets inside one [AppDatabase.withTransaction] with a single
 * `nowMs`, so a crash mid-cascade rolls back rather than orphaning a tombstone.
 */
@Singleton
class WorkoutSessionRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val sessionDao: WorkoutSessionDao,
    private val setDao: SessionSetDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
) : WorkoutSessionRepository {

    // ---------------------------------------------------------------- Reads

    override fun observeActiveSession(): Flow<WorkoutSession?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        emitAll(sessionDao.observeActiveSession(userId).map { it?.toDomain() })
    }.flowOn(Dispatchers.IO)

    override fun observeSessionsForDay(localDate: String): Flow<List<WorkoutSession>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(sessionDao.observeForDay(userId, localDate).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override fun observeRecentSessions(startDate: String, endDate: String): Flow<List<WorkoutSession>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            sessionDao.observeRecentForUser(userId, startDate, endDate)
                .map { rows -> rows.map { it.toDomain() } }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeSets(sessionId: String): Flow<List<SessionSet>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(setDao.observeForSession(sessionId).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override suspend fun getSession(id: String): WorkoutSession? = sessionDao.getById(id)?.toDomain()

    override suspend fun activeSessionId(): String? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        return sessionDao.getActiveSession(userId)?.id
    }

    // -------------------------------------------------------------- Sessions

    override suspend fun startSession(
        activityType: String,
        planId: String?,
        dayId: String?,
        title: String?,
    ): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.WORKOUT_SESSION, "startSession with no signed-in user; dropping write")
            return null
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        val localDate = LocalDate.now(ZoneId.systemDefault()).toString()
        val id = UUID.randomUUID().toString()

        // Enforce single-active: abandon any existing active session, then open the new one — atomic
        // so there is never a window with two active sessions.
        db.withTransaction {
            sessionDao.getActiveSession(userId)?.let { active ->
                sessionDao.upsert(
                    active.copy(
                        status = SessionStatus.ABANDONED.wire,
                        updatedAtEpochMs = nowMs,
                        updatedAtTzOffsetMinutes = tz,
                        isSynced = false,
                    )
                )
            }
            sessionDao.upsert(
                WorkoutSessionEntity(
                    id = id,
                    userId = userId,
                    planId = planId,
                    dayId = dayId,
                    title = title,
                    activityType = activityType,
                    startEpochMs = nowMs,
                    endEpochMs = null,
                    durationSeconds = null,
                    status = SessionStatus.ACTIVE.wire,
                    localDate = localDate,
                    note = null,
                    totalVolumeKg = null,
                    caloriesEstimate = null,
                    updatedAtEpochMs = nowMs,
                    updatedAtTzOffsetMinutes = tz,
                    deletedAtEpochMs = null,
                    isSynced = false,
                )
            )
        }
        AppLogger.d(FeatureTag.WORKOUT_SESSION, "session $id ($activityType) started; requesting sync")
        syncScheduler.requestSync()
        return id
    }

    override suspend fun finishSession(sessionId: String, caloriesEstimate: Double?) {
        val existing = sessionDao.getById(sessionId)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_SESSION, "finishSession for unknown/deleted id=$sessionId; ignoring")
            return
        }
        if (existing.status == SessionStatus.COMPLETED.wire) {
            AppLogger.w(FeatureTag.WORKOUT_SESSION, "finishSession for already-finished id=$sessionId; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        val sets = setDao.getForSession(sessionId)
        val totalVolume = sets.sumOf { it.toDomain().volumeKg ?: 0.0 }.takeIf { it > 0.0 }
        val durationSeconds = ((nowMs - existing.startEpochMs) / 1000L).toInt().coerceAtLeast(0)
        sessionDao.upsert(
            existing.copy(
                endEpochMs = nowMs,
                durationSeconds = durationSeconds,
                status = SessionStatus.COMPLETED.wire,
                totalVolumeKg = totalVolume,
                caloriesEstimate = caloriesEstimate ?: existing.caloriesEstimate,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = tz,
                isSynced = false,
            )
        )
        AppLogger.d(FeatureTag.WORKOUT_SESSION, "session $sessionId finished (${sets.size} sets, vol=$totalVolume); requesting sync")
        syncScheduler.requestSync()
    }

    override suspend fun abandonSession(sessionId: String) {
        val existing = sessionDao.getById(sessionId)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_SESSION, "abandonSession for unknown/deleted id=$sessionId; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        sessionDao.upsert(
            existing.copy(
                status = SessionStatus.ABANDONED.wire,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    override suspend fun deleteSession(id: String) {
        val existing = sessionDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.WORKOUT_SESSION, "deleteSession for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        db.withTransaction {
            setDao.getForSession(id).forEach { set ->
                setDao.upsert(
                    set.copy(
                        updatedAtEpochMs = nowMs,
                        updatedAtTzOffsetMinutes = tz,
                        deletedAtEpochMs = nowMs,
                        isSynced = false,
                    )
                )
            }
            sessionDao.upsert(
                existing.copy(
                    updatedAtEpochMs = nowMs,
                    updatedAtTzOffsetMinutes = tz,
                    deletedAtEpochMs = nowMs,
                    isSynced = false,
                )
            )
        }
        AppLogger.d(FeatureTag.WORKOUT_SESSION, "session id=$id + sets tombstoned; requesting sync")
        syncScheduler.requestSync()
    }

    // ------------------------------------------------------------------ Sets

    override suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        plannedExerciseId: String?,
        reps: Int?,
        weightKg: Double?,
        durationSeconds: Int?,
        distanceKm: Double?,
        rpe: Double?,
        isWarmup: Boolean,
    ): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.SESSION_SET, "logSet with no signed-in user; dropping write")
            return null
        }
        val session = sessionDao.getById(sessionId)
        if (session == null || session.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.SESSION_SET, "logSet for unknown/deleted session=$sessionId; dropping write")
            return null
        }
        if (session.status != SessionStatus.ACTIVE.wire) {
            AppLogger.w(FeatureTag.SESSION_SET, "logSet for non-active session=$sessionId; dropping write")
            return null
        }
        val nowMs = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        val id = UUID.randomUUID().toString()
        // Append after the exercise's current sets within this session: orderIndex groups by
        // exercise, setNumber counts within the exercise.
        val existingSets = setDao.getForSession(sessionId)
        val forExercise = existingSets.filter { it.exerciseId == exerciseId }
        val orderIndex = forExercise.firstOrNull()?.orderIndex
            ?: ((existingSets.maxOfOrNull { it.orderIndex } ?: -1) + 1)
        val setNumber = (forExercise.maxOfOrNull { it.setNumber } ?: 0) + 1
        setDao.upsert(
            SessionSetEntity(
                id = id,
                sessionId = sessionId,
                userId = userId,
                plannedExerciseId = plannedExerciseId,
                exerciseId = exerciseId,
                orderIndex = orderIndex,
                setNumber = setNumber,
                reps = reps,
                weightKg = weightKg,
                durationSeconds = durationSeconds,
                distanceKm = distanceKm,
                rpe = rpe,
                isWarmup = isWarmup,
                isCompleted = true,
                isSkipped = false,
                loggedAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = tz,
                deletedAtEpochMs = null,
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
        return id
    }

    override suspend fun editSet(set: SessionSet) {
        val existing = setDao.getById(set.id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.SESSION_SET, "editSet for unknown/deleted id=${set.id}; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        setDao.upsert(
            existing.copy(
                reps = set.reps,
                weightKg = set.weightKg,
                durationSeconds = set.durationSeconds,
                distanceKm = set.distanceKm,
                rpe = set.rpe,
                isWarmup = set.isWarmup,
                isCompleted = set.isCompleted,
                isSkipped = set.isSkipped,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    override suspend fun skipSet(id: String) {
        val existing = setDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.SESSION_SET, "skipSet for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        setDao.upsert(
            existing.copy(
                isSkipped = true,
                isCompleted = false,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    override suspend fun deleteSet(id: String) {
        val existing = setDao.getById(id)
        if (existing == null || existing.deletedAtEpochMs != null) {
            AppLogger.w(FeatureTag.SESSION_SET, "deleteSet for unknown/deleted id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        setDao.upsert(
            existing.copy(
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = nowMs,
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    override suspend fun lastCompletedSet(exerciseId: String): SessionSet? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        return setDao.getLastCompletedSet(userId, exerciseId, Timestamps.nowEpochMs())?.toDomain()
    }

    // ---------------------------------------------------------------- Mappers

    private fun WorkoutSessionEntity.toDomain() = WorkoutSession(
        id = id,
        userId = userId,
        planId = planId,
        dayId = dayId,
        title = title,
        activityType = activityType,
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
        durationSeconds = durationSeconds,
        status = SessionStatus.fromWire(status),
        localDate = localDate,
        note = note,
        totalVolumeKg = totalVolumeKg,
        caloriesEstimate = caloriesEstimate,
        updatedAt = updatedAtEpochMs,
    )

    private fun SessionSetEntity.toDomain() = SessionSet(
        id = id,
        sessionId = sessionId,
        userId = userId,
        plannedExerciseId = plannedExerciseId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        durationSeconds = durationSeconds,
        distanceKm = distanceKm,
        rpe = rpe,
        isWarmup = isWarmup,
        isCompleted = isCompleted,
        isSkipped = isSkipped,
        loggedAt = loggedAtEpochMs,
        updatedAt = updatedAtEpochMs,
    )
}
