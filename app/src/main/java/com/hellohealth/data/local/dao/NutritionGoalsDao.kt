package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.NutritionGoalsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionGoalsDao {

    /** Observe the live (non-tombstoned) nutrition-goals row for a user. */
    @Query("SELECT * FROM nutrition_goals WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    fun observe(userId: String): Flow<NutritionGoalsEntity?>

    @Query("SELECT * FROM nutrition_goals WHERE userId = :userId AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun get(userId: String): NutritionGoalsEntity?

    @Upsert
    suspend fun upsert(entity: NutritionGoalsEntity)

    /** Rows with local changes not yet pushed to Supabase (includes tombstones). */
    @Query("SELECT * FROM nutrition_goals WHERE isSynced = 0")
    suspend fun getUnsynced(): List<NutritionGoalsEntity>

    @Query("UPDATE nutrition_goals SET isSynced = 1 WHERE userId = :userId AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(userId: String, updatedAtEpochMs: Long)
}
