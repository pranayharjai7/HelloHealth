package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `workout_plans` — top of the planning hierarchy. Mirrors the multi-row syncer contract
 * (see [EmotionRecordsDao]): observe/read queries filter tombstones (`deletedAtEpochMs IS NULL`);
 * [getUnsynced] deliberately does NOT (tombstones must push so deletes propagate); [markSynced] is
 * version-exact so a newer concurrent local edit stays unsynced.
 *
 * Cascade delete is NOT here — it spans three tables and lives in a repository `@Transaction`
 * ([com.hellohealth.data.repository.WorkoutPlanRepositoryImpl]) so parent+descendants tombstone
 * atomically with one clock. This DAO only exposes the single-table tombstone stamp it contributes.
 */
@Dao
interface WorkoutPlanDao {

    /** Live plans for a user, newest first — drives the Routines list and the hero card. */
    @Query(
        "SELECT * FROM workout_plans WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "ORDER BY createdAtEpochMs DESC"
    )
    fun observeForUser(userId: String): Flow<List<WorkoutPlanEntity>>

    /** The single active live plan, if any — drives the hero card summary. */
    @Query(
        "SELECT * FROM workout_plans WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND isActive = 1 ORDER BY createdAtEpochMs DESC LIMIT 1"
    )
    fun observeActive(userId: String): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM workout_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutPlanEntity?

    @Upsert
    suspend fun upsert(entity: WorkoutPlanEntity)

    /** Clear the active flag on all of a user's plans — repo enforces at-most-one-active. */
    @Query(
        "UPDATE workout_plans SET isActive = 0, updatedAtEpochMs = :nowMs, " +
            "updatedAtTzOffsetMinutes = :tzOffsetMinutes, isSynced = 0 " +
            "WHERE userId = :userId AND isActive = 1 AND deletedAtEpochMs IS NULL"
    )
    suspend fun clearActiveForUser(userId: String, nowMs: Long, tzOffsetMinutes: Int)

    /** Tombstone one plan row (the parent step of the cascade transaction). */
    @Query(
        "UPDATE workout_plans SET deletedAtEpochMs = :nowMs, updatedAtEpochMs = :nowMs, " +
            "updatedAtTzOffsetMinutes = :tzOffsetMinutes, isSynced = 0 " +
            "WHERE id = :id AND deletedAtEpochMs IS NULL"
    )
    suspend fun tombstone(id: String, nowMs: Long, tzOffsetMinutes: Int)

    @Query("SELECT * FROM workout_plans WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutPlanEntity>

    @Query("UPDATE workout_plans SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
