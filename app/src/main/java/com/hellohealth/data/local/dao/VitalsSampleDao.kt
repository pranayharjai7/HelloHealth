package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.VitalsSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `vitals_samples`. Same multi-row syncer contract as [WorkoutPlanDao]/[EmotionRecordsDao]:
 * observe/read queries filter tombstones (`deletedAtEpochMs IS NULL`); [getUnsynced] does NOT (so
 * deletes propagate); [markSynced] is version-exact so a newer concurrent local edit stays unsynced.
 *
 * The readiness calculation reads exclusively through [observeRollupsForUser] — the persisted daily
 * rollup — never live Health Connect.
 */
@Dao
interface VitalsSampleDao {

    /** All live samples for a user, newest first. */
    @Query(
        "SELECT * FROM vitals_samples WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "ORDER BY timestampUtcEpochMs DESC"
    )
    fun observeForUser(userId: String): Flow<List<VitalsSampleEntity>>

    /** Live daily rollups within an inclusive ISO date window, ascending — feeds readiness/trends. */
    @Query(
        "SELECT * FROM vitals_samples WHERE userId = :userId AND kind = 'rollup' " +
            "AND deletedAtEpochMs IS NULL AND localDate >= :startDate AND localDate <= :endDate " +
            "ORDER BY localDate ASC"
    )
    fun observeRollupsForUser(userId: String, startDate: String, endDate: String): Flow<List<VitalsSampleEntity>>

    /** The most recent live rollup row for a user, if any — feeds the dashboard card's latest chips. */
    @Query(
        "SELECT * FROM vitals_samples WHERE userId = :userId AND kind = 'rollup' " +
            "AND deletedAtEpochMs IS NULL ORDER BY localDate DESC LIMIT 1"
    )
    fun observeLatestRollup(userId: String): Flow<VitalsSampleEntity?>

    /** The rollup row for a specific local day, if any (used by the count-gated backfill). */
    @Query(
        "SELECT * FROM vitals_samples WHERE userId = :userId AND kind = 'rollup' " +
            "AND localDate = :localDate AND deletedAtEpochMs IS NULL LIMIT 1"
    )
    suspend fun getRollupForDate(userId: String, localDate: String): VitalsSampleEntity?

    /** How many live rollup rows a user has — count gate for the backfill. */
    @Query(
        "SELECT COUNT(*) FROM vitals_samples WHERE userId = :userId AND kind = 'rollup' " +
            "AND deletedAtEpochMs IS NULL"
    )
    suspend fun countRollups(userId: String): Int

    @Query("SELECT * FROM vitals_samples WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VitalsSampleEntity?

    @Upsert
    suspend fun upsert(entity: VitalsSampleEntity)

    @Query("SELECT * FROM vitals_samples WHERE isSynced = 0")
    suspend fun getUnsynced(): List<VitalsSampleEntity>

    @Query("UPDATE vitals_samples SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
