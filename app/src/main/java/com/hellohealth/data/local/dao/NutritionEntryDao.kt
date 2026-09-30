package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.NutritionEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `nutrition_entries`. Same multi-row syncer contract as [VitalsSampleDao]/[EmotionRecordsDao]:
 * observe/read queries filter tombstones (`deletedAtEpochMs IS NULL`); [getUnsynced] does NOT (so
 * deletes propagate); [markSynced] is version-exact so a newer concurrent local edit stays unsynced.
 *
 * Food and water share the table via [NutritionEntryEntity.kind]; the food reads filter
 * `kind = 'food'` and the water reads filter `kind = 'water'`.
 */
@Dao
interface NutritionEntryDao {

    /** Live food rows for a user on a local day, newest first — feeds the day's meal sections. */
    @Query(
        "SELECT * FROM nutrition_entries WHERE userId = :userId AND localDate = :localDate " +
            "AND kind = 'food' AND deletedAtEpochMs IS NULL ORDER BY timestampUtcEpochMs DESC"
    )
    fun observeFoodForDate(userId: String, localDate: String): Flow<List<NutritionEntryEntity>>

    /** Live water rows for a user on a local day — feeds the water total. */
    @Query(
        "SELECT * FROM nutrition_entries WHERE userId = :userId AND localDate = :localDate " +
            "AND kind = 'water' AND deletedAtEpochMs IS NULL ORDER BY timestampUtcEpochMs DESC"
    )
    fun observeWaterForDate(userId: String, localDate: String): Flow<List<NutritionEntryEntity>>

    /** All live rows (food + water) for a user within an inclusive ISO date window — reserved for trends. */
    @Query(
        "SELECT * FROM nutrition_entries WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND localDate >= :startDate AND localDate <= :endDate ORDER BY localDate ASC"
    )
    fun observeInRange(userId: String, startDate: String, endDate: String): Flow<List<NutritionEntryEntity>>

    @Query("SELECT * FROM nutrition_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NutritionEntryEntity?

    @Upsert
    suspend fun upsert(entity: NutritionEntryEntity)

    @Query("SELECT * FROM nutrition_entries WHERE isSynced = 0")
    suspend fun getUnsynced(): List<NutritionEntryEntity>

    @Query("UPDATE nutrition_entries SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
