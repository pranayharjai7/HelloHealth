package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.PlannedExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `planned_exercises` — leaf of the planning hierarchy. Same syncer contract as its parents:
 * reads filter tombstones; [getUnsynced] does not; [markSynced] is version-exact.
 *
 * [tombstoneForDay]/[tombstoneForPlan] are the cascade's leaf step (invoked only from the
 * repository's cascade `@Transaction`). [tombstoneForPlan] tombstones every planned row whose parent
 * day belongs to the plan via a subquery, so a plan delete needs one call, not one-per-day.
 */
@Dao
interface PlannedExerciseDao {

    /** Live planned exercises of a day, in display order — drives the Day detail screen. */
    @Query(
        "SELECT * FROM planned_exercises WHERE dayId = :dayId AND deletedAtEpochMs IS NULL " +
            "ORDER BY orderIndex ASC"
    )
    fun observeForDay(dayId: String): Flow<List<PlannedExerciseEntity>>

    @Query("SELECT * FROM planned_exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlannedExerciseEntity?

    /** Highest order index among a day's live rows — repo appends new exercises after it. */
    @Query(
        "SELECT MAX(orderIndex) FROM planned_exercises WHERE dayId = :dayId AND deletedAtEpochMs IS NULL"
    )
    suspend fun maxOrderIndexForDay(dayId: String): Int?

    @Upsert
    suspend fun upsert(entity: PlannedExerciseEntity)

    /** Tombstone one planned row. */
    @Query(
        "UPDATE planned_exercises SET deletedAtEpochMs = :nowMs, updatedAtEpochMs = :nowMs, " +
            "updatedAtTzOffsetMinutes = :tzOffsetMinutes, isSynced = 0 " +
            "WHERE id = :id AND deletedAtEpochMs IS NULL"
    )
    suspend fun tombstone(id: String, nowMs: Long, tzOffsetMinutes: Int)

    /** Tombstone all live planned rows of a day (cascade leaf step for deleteDay). */
    @Query(
        "UPDATE planned_exercises SET deletedAtEpochMs = :nowMs, updatedAtEpochMs = :nowMs, " +
            "updatedAtTzOffsetMinutes = :tzOffsetMinutes, isSynced = 0 " +
            "WHERE dayId = :dayId AND deletedAtEpochMs IS NULL"
    )
    suspend fun tombstoneForDay(dayId: String, nowMs: Long, tzOffsetMinutes: Int)

    /** Tombstone all live planned rows whose parent day belongs to a plan (cascade leaf for deletePlan). */
    @Query(
        "UPDATE planned_exercises SET deletedAtEpochMs = :nowMs, updatedAtEpochMs = :nowMs, " +
            "updatedAtTzOffsetMinutes = :tzOffsetMinutes, isSynced = 0 " +
            "WHERE deletedAtEpochMs IS NULL AND dayId IN " +
            "(SELECT id FROM workout_days WHERE planId = :planId)"
    )
    suspend fun tombstoneForPlan(planId: String, nowMs: Long, tzOffsetMinutes: Int)

    @Query("SELECT * FROM planned_exercises WHERE isSynced = 0")
    suspend fun getUnsynced(): List<PlannedExerciseEntity>

    @Query("UPDATE planned_exercises SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
