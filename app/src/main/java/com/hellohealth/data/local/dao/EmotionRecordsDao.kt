package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.EmotionRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `emotion_records`. Mirrors the multi-row SnapshotDao contract every syncer depends on:
 * observe/read queries filter out tombstones (`deletedAtEpochMs IS NULL`); [getUnsynced] deliberately
 * does NOT (tombstones must push so deletes propagate); [markSynced] is version-exact so a newer
 * concurrent local edit stays unsynced.
 */
@Dao
interface EmotionRecordsDao {

    /** Live logs for a local day, newest first — for "today's dominant" and the dashboard card. */
    @Query(
        "SELECT * FROM emotion_records WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND localDate = :localDate ORDER BY timestampUtcEpochMs DESC"
    )
    fun observeForDay(userId: String, localDate: String): Flow<List<EmotionRecordEntity>>

    /** The single most-recent live log — drives the mood theme tint. */
    @Query(
        "SELECT * FROM emotion_records WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "ORDER BY timestampUtcEpochMs DESC LIMIT 1"
    )
    fun observeLatest(userId: String): Flow<EmotionRecordEntity?>

    /** Live logs within an inclusive ISO date window, ascending — for insights. */
    @Query(
        "SELECT * FROM emotion_records WHERE userId = :userId AND deletedAtEpochMs IS NULL " +
            "AND localDate >= :startDate AND localDate <= :endDate ORDER BY timestampUtcEpochMs ASC"
    )
    fun observeWindow(userId: String, startDate: String, endDate: String): Flow<List<EmotionRecordEntity>>

    @Query("SELECT * FROM emotion_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EmotionRecordEntity?

    @Upsert
    suspend fun upsert(entity: EmotionRecordEntity)

    @Query("SELECT * FROM emotion_records WHERE isSynced = 0")
    suspend fun getUnsynced(): List<EmotionRecordEntity>

    @Query("UPDATE emotion_records SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
