package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.AchievementEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `achievements`. Same multi-row syncer contract as [SessionSetDao]: observe/read queries
 * filter tombstones; [getUnsynced] does NOT; [markSynced] is version-exact. [getByCode] backs the
 * one-time-unlock guard (the repository only inserts when there's no existing live row for the code).
 */
@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE userId = :userId AND deletedAtEpochMs IS NULL ORDER BY unlockedAtEpochMs DESC")
    fun observeForUser(userId: String): Flow<List<AchievementEntity>>

    /** One-shot live achievements for a user (recompute reads that must see just-written rows). */
    @Query("SELECT * FROM achievements WHERE userId = :userId AND deletedAtEpochMs IS NULL ORDER BY unlockedAtEpochMs DESC")
    suspend fun snapshotForUser(userId: String): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE id = :id AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun getById(id: String): AchievementEntity?

    @Upsert
    suspend fun upsert(entity: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE isSynced = 0")
    suspend fun getUnsynced(): List<AchievementEntity>

    @Query("UPDATE achievements SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
