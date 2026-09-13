package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.WorkoutSessionDao
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import com.hellohealth.domain.model.WorkoutActivityType
import com.hellohealth.domain.model.WorkoutSession
import com.hellohealth.domain.repository.WorkoutRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first workout repository. Writes hit Room with `isSynced=false` then poke [SyncScheduler];
 * the [com.hellohealth.sync.WorkoutSessionSyncer] owns the Supabase round-trip. Reads are Room Flows.
 *
 * With no signed-in user, reads emit empty and writes are dropped with a warning — matching the
 * [EmotionsRepositoryImpl] contract so screens never special-case a missing session.
 *
 * [saveWorkout] mints a random UUID id (per the Phase-A decision) so two distinct workouts logged
 * in the same instant never collide — unlike Emotions' deterministic id, a manual workout has no
 * natural dedup key. Save is invoked from an explicit Save tap that completes before navigation, so
 * `viewModelScope` is correct; no `@ApplicationScope` is needed in Phase A.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler
) : WorkoutRepository {

    override fun observeWorkouts(): Flow<List<WorkoutSession>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(workoutSessionDao.observeForUser(userId).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override fun observeForDay(localDate: String): Flow<List<WorkoutSession>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(workoutSessionDao.observeForDay(userId, localDate).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override suspend fun saveWorkout(
        activityType: WorkoutActivityType,
        title: String?,
        startTimeUtcEpochMs: Long,
        endTimeUtcEpochMs: Long,
        durationMinutes: Long,
        calories: Double?,
        distanceKm: Double?,
        note: String?
    ): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.WORKOUT, "saveWorkout with no signed-in user; dropping write")
            return null
        }

        val nowMs = Timestamps.nowEpochMs()
        val zone = ZoneId.systemDefault()
        // Denormalize the local day from the workout's START time so a session sorts on the day it
        // began (a late-night session that crosses midnight still lives on its start day).
        val localDate = Timestamps.epochMsToInstant(startTimeUtcEpochMs).atZone(zone).toLocalDate().toString()

        // Random UUID: a manual workout has no natural dedup key, so two distinct workouts logged in
        // the same instant must be distinct rows (never a silent overwrite).
        val id = UUID.randomUUID().toString()

        workoutSessionDao.upsert(
            WorkoutSessionEntity(
                id = id,
                userId = userId,
                activityType = activityType.name,
                title = title,
                startTimeUtcEpochMs = startTimeUtcEpochMs,
                endTimeUtcEpochMs = endTimeUtcEpochMs,
                durationMinutes = durationMinutes,
                calories = calories,
                distanceKm = distanceKm,
                note = note,
                localDate = localDate,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                deletedAtEpochMs = null,
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.WORKOUT, "workout '${activityType.name}' logged locally; requesting sync")
        syncScheduler.requestSync()
        return id
    }

    override suspend fun delete(id: String) {
        val existing = workoutSessionDao.getById(id)
        if (existing == null) {
            AppLogger.w(FeatureTag.WORKOUT, "delete for unknown workout id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        workoutSessionDao.upsert(
            existing.copy(
                deletedAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.WORKOUT, "workout id=$id tombstoned; requesting sync")
        syncScheduler.requestSync()
    }

    private fun WorkoutSessionEntity.toDomain() = WorkoutSession(
        id = id,
        userId = userId,
        activityType = WorkoutActivityType.fromName(activityType),
        title = title,
        startTimeUtcEpochMs = startTimeUtcEpochMs,
        endTimeUtcEpochMs = endTimeUtcEpochMs,
        durationMinutes = durationMinutes,
        calories = calories,
        distanceKm = distanceKm,
        note = note,
        localDate = localDate
    )
}
