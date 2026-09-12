package com.hellohealth.sync

import com.hellohealth.core.logging.FeatureTag

/**
 * Per-table sync strategy. One implementation per Supabase-backed table (goals, profile,
 * food_prefs, snapshot). The [SyncOrchestrator] runs every bound Syncer each pass.
 *
 * Contract:
 *  - [push] sends local unsynced rows (including tombstones) to Supabase and, on success, flips
 *    their local `isSynced` flag. Local edits win exact-millis ties (push runs before pull).
 *  - [pull] fetches remote rows and applies any that are strictly newer than the local row via
 *    [LwwResolver], writing them to Room as already-synced.
 *  - Neither may throw for an expected/transient condition (offline, missing row). Throw only on a
 *    genuine failure; the orchestrator counts it and asks WorkManager to retry — the row stays
 *    `isSynced=false`, so nothing is ever dropped.
 */
interface Syncer {

    /** Identifies this syncer in logs and `sync_log` rows. */
    val featureTag: FeatureTag

    /** Push local unsynced rows for [userId]. @return count pushed. */
    suspend fun push(userId: String): Int

    /** Pull remote rows for [userId] and apply LWW winners locally. @return count applied. */
    suspend fun pull(userId: String): Int
}
