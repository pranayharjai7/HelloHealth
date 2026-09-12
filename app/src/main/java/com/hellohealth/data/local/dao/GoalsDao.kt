package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.GoalsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalsDao {

    /** Observe the live (non-tombstoned) goals row for a user. */
    @Query("SELECT * FROM goals WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    fun observe(userId: String): Flow<GoalsEntity?>

    @Query("SELECT * FROM goals WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun get(userId: String): GoalsEntity?

    @Upsert
    suspend fun upsert(entity: GoalsEntity)

    /** Rows with local changes not yet pushed to Supabase (includes tombstones). */
    @Query("SELECT * FROM goals WHERE isSynced = 0")
    suspend fun getUnsynced(): List<GoalsEntity>

    @Query("UPDATE goals SET isSynced = 1 WHERE userId = :userId AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(userId: String, updatedAtEpochMs: Long)
}
