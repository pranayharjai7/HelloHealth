package com.hellohealth.domain.repository

import com.hellohealth.domain.model.SessionSet
import com.hellohealth.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

/**
 * Owns the two live-logging tables — [WorkoutSession]s and their [SessionSet]s — as one repository
 * so the session → sets relationship has a single home and finish/abandon can tombstone atomically.
 *
 * Contract (mirrors [WorkoutPlanRepository]/[EmotionsRepository] so screens never special-case a
 * missing session): every method resolves the current user id; with no signed-in user, reads emit
 * empty/null and writes are dropped with a warning (never throw) returning null. Writes stamp the
 * UTC-epoch-ms LWW clock + `isSynced=false` then request a sync; deletes are soft tombstones.
 *
 * Single-active-session invariant: at most one session per user is [com.hellohealth.domain.model.SessionStatus.ACTIVE]
 * at a time. [startSession] abandons any currently-active session before opening the new one.
 */
interface WorkoutSessionRepository {

    /** The current user's single active session, or null (also null when signed out). */
    fun observeActiveSession(): Flow<WorkoutSession?>

    /** Live sessions on [localDate], newest first — feeds the Health Activity Log (empty signed out). */
    fun observeSessionsForDay(localDate: String): Flow<List<WorkoutSession>>

    /** Live sessions in the inclusive ISO date window, newest first — history/trends. */
    fun observeRecentSessions(startDate: String, endDate: String): Flow<List<WorkoutSession>>

    /** Live sets of a session, in logging order — feeds the active-workout screen. */
    fun observeSets(sessionId: String): Flow<List<SessionSet>>

    suspend fun getSession(id: String): WorkoutSession?

    /** The current user's active session id, or null — a one-shot guard against double-start. */
    suspend fun activeSessionId(): String?

    /**
     * Start a session. Abandons any currently-active session first (single-active invariant), then
     * opens a new ACTIVE session. [planId]/[dayId]/[title] optionally anchor it to a planned day;
     * all null for an ad-hoc session. Returns the new session id, or null if signed out.
     */
    suspend fun startSession(
        activityType: String,
        planId: String? = null,
        dayId: String? = null,
        title: String? = null,
    ): String?

    /**
     * Append a logged set to [sessionId]. [setNumber]/[orderIndex] default to the next slot for the
     * exercise within the session when not given. Returns the new set id, or null if signed out /
     * the session is unknown or not active.
     */
    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        plannedExerciseId: String? = null,
        reps: Int? = null,
        weightKg: Double? = null,
        durationSeconds: Int? = null,
        distanceKm: Double? = null,
        rpe: Double? = null,
        isWarmup: Boolean = false,
    ): String?

    /** Persist edits to a set. The caller passes the full desired [SessionSet] (id must exist). */
    suspend fun editSet(set: SessionSet)

    /** Mark a set skipped (kept in the log, excluded from volume). */
    suspend fun skipSet(id: String)

    /** Soft-delete a single set. */
    suspend fun deleteSet(id: String)

    /**
     * Finish [sessionId]: stamp end + duration, recompute [WorkoutSession.totalVolumeKg] from its
     * live sets, set status COMPLETED. [caloriesEstimate] is passed through when the caller has one.
     * No-op for an unknown/already-finished session.
     */
    suspend fun finishSession(sessionId: String, caloriesEstimate: Double? = null)

    /** Abandon [sessionId]: set status ABANDONED (kept, not tombstoned). No-op if unknown. */
    suspend fun abandonSession(sessionId: String)

    /** Soft-delete a session AND all its sets atomically (one tombstone clock). */
    suspend fun deleteSession(id: String)

    /**
     * The most recent completed working set for [exerciseId] before now (across any session), for
     * prefill-from-last-set. Null when there's no prior set / signed out. Excludes warmups and skips.
     */
    suspend fun lastCompletedSet(exerciseId: String): SessionSet?
}
