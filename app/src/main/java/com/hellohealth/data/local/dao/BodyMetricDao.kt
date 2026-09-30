package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.BodyMetricEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `body_metrics`. Same multi-row syncer contract as [VitalsSampleDao]: observe/read queries
 * filter tombstones (`deletedAtEpochMs IS NULL`); [getUnsynced] does NOT (so deletes propagate);
 * [markSynced] is version-exact so a newer concurrent local edit stays unsynced.
 */
@Dao
interface BodyMetricDao {

    /** Live body-metric rows within an inclusive ISO date window, ascending — feeds the trends. */
    @Query(
        "SELECT * FROM body_metrics WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND localDate >= :startDate AND localDate <= :endDate ORDER BY localDate ASC"
    )
    fun observeRecentForUser(userId: String, startDate: String, endDate: String): Flow<List<BodyMetricEntity>>

    /** The most recent live row for a user, if any — the "current" body snapshot. */
    @Query(
        "SELECT * FROM body_metrics WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "ORDER BY localDate DESC LIMIT 1"
    )
    fun observeLatest(userId: String): Flow<BodyMetricEntity?>

    /** The row for a specific local day, if any (used by the idempotent Health-Connect-daily capture). */
    @Query(
        "SELECT * FROM body_metrics WHERE userId = :userId AND localDate = :localDate " +
            "AND deletedAtEpochMs IS NULL LIMIT 1"
    )
    suspend fun getForDate(userId: String, localDate: String): BodyMetricEntity?

    @Query("SELECT * FROM body_metrics WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BodyMetricEntity?

    @Upsert
    suspend fun upsert(entity: BodyMetricEntity)

    @Query("SELECT * FROM body_metrics WHERE isSynced = 0")
    suspend fun getUnsynced(): List<BodyMetricEntity>

    @Query("UPDATE body_metrics SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
