package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun get(userId: String): ProfileEntity?

    @Query("SELECT * FROM profile WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    fun observe(userId: String): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(entity: ProfileEntity)

    @Query("SELECT * FROM profile WHERE isSynced = 0")
    suspend fun getUnsynced(): List<ProfileEntity>

    @Query("UPDATE profile SET isSynced = 1 WHERE userId = :userId AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(userId: String, updatedAtEpochMs: Long)
}
