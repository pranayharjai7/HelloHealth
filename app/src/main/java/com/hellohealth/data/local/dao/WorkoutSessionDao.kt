package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `workout_sessions`. Mirrors the multi-row Emotions DAO contract every syncer depends on:
 * observe/read queries filter out tombstones (`deletedAtEpochMs IS NULL`); [getUnsynced] deliberately
 * does NOT (tombstones must push so deletes propagate); [markSynced] is version-exact so a newer
 * concurrent local edit stays unsynced.
 */
@Dao
interface WorkoutSessionDao {

    /** All live (non-deleted) workouts for a user, newest first — for the workout-log section. */
    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "ORDER BY startTimeUtcEpochMs DESC"
    )
    fun observeForUser(userId: String): Flow<List<WorkoutSessionEntity>>

    /** Live workouts for a local day, newest first — for day-scoped rollups. */
    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND localDate = :localDate ORDER BY startTimeUtcEpochMs DESC"
    )
    fun observeForDay(userId: String, localDate: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutSessionEntity?

    @Upsert
    suspend fun upsert(entity: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
