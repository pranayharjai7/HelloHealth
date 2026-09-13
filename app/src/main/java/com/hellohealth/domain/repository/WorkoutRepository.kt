package com.hellohealth.domain.repository

import com.hellohealth.domain.model.WorkoutActivityType
import com.hellohealth.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes manually-logged workout sessions. Room-first: writes land locally
 * (`isSynced=false`) and a background sync reconciles with Supabase; reads are Room Flows. Mirrors
 * the [EmotionsRepository] contract — the impl knows nothing about Postgrest (the
 * [com.hellohealth.sync.WorkoutSessionSyncer] owns the wire).
 *
 * Returns empty (never throws) when there is no signed-in user, so callers stay simple. This is the
 * Phase-A manual path only: it neither reads from nor writes to Health Connect (that is Phase D),
 * so the existing read-only HC activity path is entirely untouched.
 */
interface WorkoutRepository {

    /** All live (non-deleted) workouts for the signed-in user, newest first. */
    fun observeWorkouts(): Flow<List<WorkoutSession>>

    /** Live workouts whose local day equals [localDate] (ISO `yyyy-MM-dd`), newest first. */
    fun observeForDay(localDate: String): Flow<List<WorkoutSession>>

    /**
     * Persist a new manual workout locally and request a sync. No-op if no user is signed in.
     * @return the stable id (a random UUID) of the written session, or null if there was no
     *   signed-in user (write dropped).
     */
    suspend fun saveWorkout(
        activityType: WorkoutActivityType,
        title: String? = null,
        startTimeUtcEpochMs: Long,
        endTimeUtcEpochMs: Long,
        durationMinutes: Long,
        calories: Double? = null,
        distanceKm: Double? = null,
        note: String? = null
    ): String?

    /** Soft-delete a workout (tombstone) and request a sync. */
    suspend fun delete(id: String)
}
