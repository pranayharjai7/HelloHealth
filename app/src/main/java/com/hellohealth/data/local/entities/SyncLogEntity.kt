package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-only audit row written once per feature per sync run. Not [com.hellohealth.data.local.Syncable]
 * — it never syncs to Supabase; it exists purely to make the reconciler observable in the hidden
 * debug screen (pushed/pulled/conflicts/failures per run).
 */
@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runAtEpochMs: Long,
    val featureTag: String,
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val failures: Int,
    val durationMs: Long,
    val resultLabel: String
)
