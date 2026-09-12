package com.hellohealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hellohealth.data.local.entities.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {

    @Insert
    suspend fun insert(entry: SyncLogEntity)

    /** Latest sync-log rows, newest first — surfaced in the hidden debug screen. */
    @Query("SELECT * FROM sync_log ORDER BY runAtEpochMs DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SyncLogEntity>>

    /** Prune old rows so the audit log can't grow unbounded. */
    @Query(
        "DELETE FROM sync_log WHERE id NOT IN " +
            "(SELECT id FROM sync_log ORDER BY runAtEpochMs DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}
