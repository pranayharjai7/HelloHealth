package com.hellohealth.domain.repository

import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes mood logs. Room-first: writes land locally (`isSynced=false`) and a background
 * sync reconciles with Supabase; reads are Room Flows. Mirrors the GoalsRepository/ProfileRepository
 * contract — the impl knows nothing about Postgrest (the EmotionsSyncer owns the wire).
 *
 * Returns empty/null (never throws) when there is no signed-in user, so callers stay simple.
 */
interface EmotionsRepository {

    /** All of today's live (non-deleted) mood logs for the signed-in user, newest first. */
    fun observeToday(): Flow<List<EmotionRecord>>

    /** The single most-recently-logged live mood, or null if none — drives the theme tint. */
    fun observeLatest(): Flow<EmotionRecord?>

    /** Live mood logs whose local day falls within the inclusive [startEpochDay, endEpochDay] window. */
    fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>>

    /** Persist a new mood log locally and request a sync. No-op (logged) if no user is signed in. */
    suspend fun logEmotion(
        emotion: EmotionType,
        confidence: Double = 1.0,
        source: String = EmotionRecord.SOURCE_MANUAL,
        note: String? = null,
        visibility: String = EmotionRecord.VISIBILITY_PRIVATE
    )

    /** Soft-delete a mood log (tombstone) and request a sync. */
    suspend fun delete(id: String)
}
