package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.StreakEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `streaks`. Same multi-row syncer contract as [SessionSetDao]: observe/read queries filter
 * tombstones; [getUnsynced] does NOT; [markSynced] is version-exact.
 */
@Dao
interface StreakDao {

    @Query("SELECT * FROM streaks WHERE userId = :userId AND deletedAtEpochMs IS NULL")
    fun observeForUser(userId: String): Flow<List<StreakEntity>>

    @Query("SELECT * FROM streaks WHERE id = :id AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun getById(id: String): StreakEntity?

    @Upsert
    suspend fun upsert(entity: StreakEntity)

    @Query("SELECT * FROM streaks WHERE isSynced = 0")
    suspend fun getUnsynced(): List<StreakEntity>

    @Query("UPDATE streaks SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
