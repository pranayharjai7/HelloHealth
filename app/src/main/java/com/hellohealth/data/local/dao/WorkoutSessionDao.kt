package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `workout_sessions`. Same multi-row syncer contract as [BodyMetricDao]: observe/read queries
 * filter tombstones (`deletedAtEpochMs IS NULL`); [getUnsynced] does NOT (so deletes propagate);
 * [markSynced] is version-exact so a newer concurrent local edit stays unsynced.
 */
@Dao
interface WorkoutSessionDao {

    /** The single live active session for a user, if any — enforces the single-active invariant. */
    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND status = 'active' " +
            "AND deletedAtEpochMs IS NULL ORDER BY startEpochMs DESC LIMIT 1"
    )
    fun observeActiveSession(userId: String): Flow<WorkoutSessionEntity?>

    /** Live sessions on a given local day, newest first — feeds the Health screen's Activity Log. */
    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND localDate = :localDate " +
            "AND deletedAtEpochMs IS NULL ORDER BY startEpochMs DESC"
    )
    fun observeForDay(userId: String, localDate: String): Flow<List<WorkoutSessionEntity>>

    /** Live sessions within an inclusive ISO date window, newest first — history / trends. */
    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND localDate >= :startDate AND localDate <= :endDate ORDER BY startEpochMs DESC"
    )
    fun observeRecentForUser(userId: String, startDate: String, endDate: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun getById(id: String): WorkoutSessionEntity?

    /** The current live active session (one-shot, for repository guards). */
    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND status = 'active' " +
            "AND deletedAtEpochMs IS NULL ORDER BY startEpochMs DESC LIMIT 1"
    )
    suspend fun getActiveSession(userId: String): WorkoutSessionEntity?

    @Upsert
    suspend fun upsert(entity: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
