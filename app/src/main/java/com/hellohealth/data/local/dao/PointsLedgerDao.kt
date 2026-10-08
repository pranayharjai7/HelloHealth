package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.PointsLedgerEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the append-only `points_ledger`. Same multi-row syncer contract as [SessionSetDao]:
 * observe/read queries filter tombstones; [getUnsynced] does NOT; [markSynced] is version-exact.
 * [totalForUser]/[totalForDay] sum the live (non-tombstoned) ledger.
 */
@Dao
interface PointsLedgerDao {

    @Query("SELECT * FROM points_ledger WHERE userId = :userId AND deletedAtEpochMs IS NULL ORDER BY localDate DESC")
    fun observeForUser(userId: String): Flow<List<PointsLedgerEntity>>

    @Query("SELECT COALESCE(SUM(points), 0) FROM points_ledger WHERE userId = :userId AND deletedAtEpochMs IS NULL")
    fun observeTotalForUser(userId: String): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(points), 0) FROM points_ledger WHERE userId = :userId " +
            "AND localDate = :localDate AND deletedAtEpochMs IS NULL"
    )
    suspend fun totalForDay(userId: String, localDate: String): Int

    /** Count of live ledger rows for a given source (e.g. awarded workouts). */
    @Query("SELECT COUNT(*) FROM points_ledger WHERE userId = :userId AND source = :source AND deletedAtEpochMs IS NULL")
    suspend fun countForSource(userId: String, source: String): Int

    /** The highest single-row points value for a source (e.g. best daily wellness score). */
    @Query("SELECT COALESCE(MAX(points), 0) FROM points_ledger WHERE userId = :userId AND source = :source AND deletedAtEpochMs IS NULL")
    suspend fun maxPointsForSource(userId: String, source: String): Int

    @Query("SELECT * FROM points_ledger WHERE id = :id AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun getById(id: String): PointsLedgerEntity?

    @Upsert
    suspend fun upsert(entity: PointsLedgerEntity)

    @Query("SELECT * FROM points_ledger WHERE isSynced = 0")
    suspend fun getUnsynced(): List<PointsLedgerEntity>

    @Query("UPDATE points_ledger SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
