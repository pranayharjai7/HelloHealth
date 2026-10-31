package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.hellohealth.data.local.entities.CachedFoodEntity

/**
 * DAO for the read-only, per-device `cached_foods` catalog (bundled common foods + resolved
 * USDA/Open Food Facts items). NOT user-owned and NOT synced — so no observe/tombstone/unsynced/
 * markSynced surface, exactly like [ExerciseDao]. [insertAll]/[upsert] use REPLACE so a re-resolve
 * refreshes the cached facts; [count] gates the idempotent bundled seed.
 */
@Dao
interface CachedFoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<CachedFoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: CachedFoodEntity)

    @Query("SELECT COUNT(*) FROM cached_foods")
    suspend fun count(): Int

    @Query("SELECT * FROM cached_foods WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedFoodEntity?

    @Query("SELECT * FROM cached_foods WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): CachedFoodEntity?

    /**
     * Token-based search: the repository builds a `WHERE (name LIKE ? ESCAPE '\') AND (…) …` clause —
     * one `%token%` pattern per whitespace-separated word — so the words match in ANY order ("breast
     * chicken" finds "Chicken Breast"). Ordering/ranking is done in Kotlin after the fetch, so this
     * raw query just filters. See [com.hellohealth.data.repository.NutritionRepositoryImpl.searchFoods].
     */
    @RawQuery
    suspend fun searchRaw(query: SupportSQLiteQuery): List<CachedFoodEntity>
}
