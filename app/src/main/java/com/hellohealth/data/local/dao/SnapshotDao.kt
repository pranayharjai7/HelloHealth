package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.SnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {

    @Query(
        "SELECT * FROM snapshot WHERE userId = :userId AND snapshotDate = :snapshotDate " +
            "AND deletedAtEpochMs IS NULL LIMIT 1"
    )
    suspend fun get(userId: String, snapshotDate: String): SnapshotEntity?

    /** Snapshots for a user within an inclusive ISO date range, ascending — for month views. */
    @Query(
        "SELECT * FROM snapshot WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND snapshotDate >= :startDate AND snapshotDate <= :endDate ORDER BY snapshotDate ASC"
    )
    suspend fun getRange(userId: String, startDate: String, endDate: String): List<SnapshotEntity>

    @Query(
        "SELECT * FROM snapshot WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND snapshotDate >= :startDate AND snapshotDate <= :endDate ORDER BY snapshotDate ASC"
    )
    fun observeRange(userId: String, startDate: String, endDate: String): Flow<List<SnapshotEntity>>

    @Upsert
    suspend fun upsert(entity: SnapshotEntity)

    @Query("SELECT * FROM snapshot WHERE isSynced = 0")
    suspend fun getUnsynced(): List<SnapshotEntity>

    @Query(
        "UPDATE snapshot SET isSynced = 1 WHERE userId = :userId AND snapshotDate = :snapshotDate " +
            "AND updatedAtEpochMs = :updatedAtEpochMs"
    )
    suspend fun markSynced(userId: String, snapshotDate: String, updatedAtEpochMs: Long)
}
