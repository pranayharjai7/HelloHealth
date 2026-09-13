package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hellohealth.data.local.entities.ExerciseEntity

/**
 * DAO for the read-only global `exercises` catalog (873 entries, seeded per-device from
 * `assets/exercises.json`). NOT user-owned and NOT synced — so no observe/tombstone/unsynced/
 * markSynced surface, unlike the three planning DAOs. [insertAll] uses REPLACE so a partial seed
 * self-heals on the next attempt; [count] gates the idempotent seed.
 */
@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ExerciseEntity>

    /**
     * Catalog search for the exercise picker: matches name or category, ordered by name. [query] is
     * matched with SQL `LIKE`; callers pass the user text and the repository wraps it with `%…%`.
     */
    @Query(
        "SELECT * FROM exercises WHERE name LIKE :query OR category LIKE :query " +
            "ORDER BY name ASC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int): List<ExerciseEntity>

    /** Distinct categories for the picker's filter chips, alphabetical. */
    @Query("SELECT DISTINCT category FROM exercises ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>
}
