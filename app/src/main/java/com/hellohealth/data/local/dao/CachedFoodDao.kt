package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
     * Typeahead search over the local catalog. [query] is a pre-built SQL `LIKE` pattern (the
     * repository wraps the user text with `%…%` and escapes literal wildcards); `ESCAPE '\'` honors
     * that escaping so a `%` typed by the user stays literal. Mirrors [ExerciseDao.search].
     */
    @Query(
        "SELECT * FROM cached_foods WHERE name LIKE :query ESCAPE '\\' ORDER BY name ASC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int): List<CachedFoodEntity>
}
