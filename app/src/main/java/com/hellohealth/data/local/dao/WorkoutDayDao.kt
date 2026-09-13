package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.WorkoutDayEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `workout_days` — middle of the planning hierarchy. Same syncer contract as
 * [WorkoutPlanDao]/[EmotionRecordsDao]: reads filter tombstones; [getUnsynced] does not; [markSynced]
 * is version-exact.
 *
 * [tombstoneForPlan] is the cascade's middle step (all live days of a plan → tombstoned with one
 * clock); it is invoked only from the repository's cascade `@Transaction`, never on its own.
 */
@Dao
interface WorkoutDayDao {

    /** Live days of a plan, in slot order — drives the Routine detail screen. */
    @Query(
        "SELECT * FROM workout_days WHERE planId = :planId AND deletedAtEpochMs IS NULL " +
            "ORDER BY slotKey ASC"
    )
    fun observeForPlan(planId: String): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_days WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutDayEntity?

    /** Live day ids of a plan — repo reads these to cascade into planned_exercises. */
    @Query("SELECT id FROM workout_days WHERE planId = :planId AND deletedAtEpochMs IS NULL")
    suspend fun liveDayIdsForPlan(planId: String): List<String>

    @Upsert
    suspend fun upsert(entity: WorkoutDayEntity)

    /** Tombstone one day row (cascade step for [WorkoutPlanRepositoryImpl.deleteDay]). */
    @Query(
        "UPDATE workout_days SET deletedAtEpochMs = :nowMs, updatedAtEpochMs = :nowMs, " +
            "updatedAtTzOffsetMinutes = :tzOffsetMinutes, isSynced = 0 " +
            "WHERE id = :id AND deletedAtEpochMs IS NULL"
    )
    suspend fun tombstone(id: String, nowMs: Long, tzOffsetMinutes: Int)

    /** Tombstone all live days of a plan (cascade middle step for deletePlan). */
    @Query(
        "UPDATE workout_days SET deletedAtEpochMs = :nowMs, updatedAtEpochMs = :nowMs, " +
            "updatedAtTzOffsetMinutes = :tzOffsetMinutes, isSynced = 0 " +
            "WHERE planId = :planId AND deletedAtEpochMs IS NULL"
    )
    suspend fun tombstoneForPlan(planId: String, nowMs: Long, tzOffsetMinutes: Int)

    @Query("SELECT * FROM workout_days WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutDayEntity>

    @Query("UPDATE workout_days SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
