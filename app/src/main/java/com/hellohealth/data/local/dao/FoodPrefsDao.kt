package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.FoodPrefsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodPrefsDao {

    @Query("SELECT * FROM food_prefs WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    fun observe(userId: String): Flow<FoodPrefsEntity?>

    @Query("SELECT * FROM food_prefs WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun get(userId: String): FoodPrefsEntity?

    @Upsert
    suspend fun upsert(entity: FoodPrefsEntity)

    @Query("SELECT * FROM food_prefs WHERE isSynced = 0")
    suspend fun getUnsynced(): List<FoodPrefsEntity>

    @Query("UPDATE food_prefs SET isSynced = 1 WHERE userId = :userId AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(userId: String, updatedAtEpochMs: Long)
}
