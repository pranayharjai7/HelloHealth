package com.hellohealth.domain.repository

import com.hellohealth.domain.model.Exercise

/**
 * Read-only access to the global exercise catalog (873 bundled entries). Local-only: the catalog is
 * seeded identically per-device from `assets/exercises.json` and is NOT user-owned and NOT synced —
 * so, unlike the user repositories, this has no session/sync surface, only seed + query.
 *
 * All methods are safe on an empty catalog (returns empty / null), so a failed seed degrades to an
 * empty picker rather than a crash.
 */
interface ExerciseRepository {

    /**
     * Idempotently seed the catalog: inserts the bundled entries only if the table is empty. A parse
     * failure is swallowed (logged) and leaves the table empty so the next call retries. Safe to call
     * on every launch. Returns the number of rows the catalog holds afterwards.
     */
    suspend fun seedIfEmpty(): Int

    /** Current catalog row count (0 when unseeded/unavailable). */
    suspend fun count(): Int

    /** Name-or-category substring search, ordered by name, capped at [limit]. Empty query matches all. */
    suspend fun search(query: String, limit: Int = 50): List<Exercise>

    /** Single exercise by id, or null if absent. */
    suspend fun getById(id: String): Exercise?

    /** Batch lookup by id → Exercise map (missing ids simply absent). Empty input → empty map. */
    suspend fun getByIds(ids: Collection<String>): Map<String, Exercise>

    /** Distinct categories for the picker filter chips, alphabetical. */
    suspend fun getAllCategories(): List<String>
}
