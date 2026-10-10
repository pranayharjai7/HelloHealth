package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hellohealth.data.local.entities.IntradaySeriesEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `intraday_series` (device-local intraday buckets). Observe/read queries filter tombstones;
 * [getUnsynced]/[markSynced] are carried for the uniform syncable contract even though intraday is not
 * synced today (no syncer reads this table — see [IntradaySeriesEntity]).
 */
@Dao
interface IntradaySeriesDao {

    /** Live buckets for a user's local day + metric, ascending by bucket start — feeds the day graphs. */
    @Query(
        "SELECT * FROM intraday_series WHERE userId = :userId AND localDate = :localDate " +
            "AND metric = :metric AND deletedAtEpochMs IS NULL ORDER BY bucketStartUtcEpochMs ASC"
    )
    fun observeSeriesForDay(userId: String, localDate: String, metric: String): Flow<List<IntradaySeriesEntity>>

    @Upsert
    suspend fun upsert(entity: IntradaySeriesEntity)

    @Upsert
    suspend fun upsertAll(entities: List<IntradaySeriesEntity>)

    /** Hard-delete all rows for a day+metric — used when a re-read finds the metric no longer has a
     *  real intraday series (e.g. a degenerate uniform spread was dropped), so no orphans linger. */
    @Query("DELETE FROM intraday_series WHERE userId = :userId AND localDate = :localDate AND metric = :metric")
    suspend fun deleteDayMetric(userId: String, localDate: String, metric: String)

    @Query("SELECT * FROM intraday_series WHERE isSynced = 0")
    suspend fun getUnsynced(): List<IntradaySeriesEntity>

    @Query("UPDATE intraday_series SET isSynced = 1 WHERE id = :id AND updatedAtEpochMs = :updatedAtEpochMs")
    suspend fun markSynced(id: String, updatedAtEpochMs: Long)
}
