package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.SessionSetEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `session_sets`. Same multi-row syncer contract as [WorkoutSessionDao]: observe/read queries
 * filter tombstones; [getUnsynced] does NOT; [markSynced] is version-exact.
 */
@Dao
interface SessionSetDao {

    /** Live sets of a session, in logging order — feeds the active-workout screen. */
    @Query(
        "SELECT * FROM session_sets WHERE sessionId = :sessionId AND deletedAtEpochMs IS NULL " +
            "ORDER BY orderIndex ASC, setNumber ASC"
    )
    fun observeForSession(sessionId: String): Flow<List<SessionSetEntity>>

    /** One-shot live sets of a session (for finish-time volume derivation). */
    @Query(
        "SELECT * FROM session_sets WHERE sessionId = :sessionId AND deletedAtEpochMs IS NULL " +
            "ORDER BY orderIndex ASC, setNumber ASC"
    )
    suspend fun getForSession(sessionId: String): List<SessionSetEntity>

    /**
     * The most recent completed set for an exercise before [beforeEpochMs], across any session —
     * powers prefill-from-last-set. Excludes warmups and skips so prefill reflects a real working set.
     */
    @Query(
        "SELECT * FROM session_sets WHERE userId = :userId AND exerciseId = :exerciseId " +
            "AND isCompleted = 1 AND isSkipped = 0 AND isWarmup = 0 AND deletedAtEpochMs IS NULL " +
            "AND loggedAtEpochMs < :beforeEpochMs ORDER BY loggedAtEpochMs DESC LIMIT 1"
    )
    suspend fun getLastCompletedSet(userId: String, exerciseId: String, beforeEpochMs: Long): SessionSetEntity?

    @Query("SELECT * FROM session_sets WHERE id = :id AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun getById(id: String): SessionSetEntity?

    @Upsert
    suspend fun upsert(entity: SessionSetEntity)

    @Query("SELECT * FROM session_sets WHERE isSynced = 0")
    suspend fun getUnsynced(): List<SessionSetEntity>

    @Query("UPDATE session_sets SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
