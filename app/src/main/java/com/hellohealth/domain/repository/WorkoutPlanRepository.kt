package com.hellohealth.domain.repository

import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.PlannedExercise
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.WorkoutDay
import com.hellohealth.domain.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow

/**
 * Owns the three user-owned planning tables — plans, days, and planned exercises — as one repository
 * so the [WorkoutPlan] → [WorkoutDay] → [PlannedExercise] hierarchy has a single obvious home and the
 * cascade delete can be one atomic transaction.
 *
 * Contract (mirrors [EmotionsRepository]/[GoalsRepository] so screens never special-case a missing
 * session): every method resolves the current user id; with no signed-in user, reads emit
 * empty/null and writes are dropped with a warning (never throw). Writes stamp the UTC-epoch-ms LWW
 * clock + `isSynced=false` then request a sync; deletes are soft tombstones. The read-only catalog
 * lookups needed to build [PlannedExerciseWithDetails] come from [ExerciseRepository].
 */
interface WorkoutPlanRepository {

    // ---- Plans ----

    /** Live plans for the current user, newest first (empty when signed out). */
    fun observePlans(): Flow<List<WorkoutPlan>>

    /** The current user's single active plan, or null (also null when signed out). */
    fun observeActivePlan(): Flow<WorkoutPlan?>

    suspend fun getPlan(id: String): WorkoutPlan?

    /**
     * Create a plan. When [makeActive] (the default for the first plan), it becomes the sole active
     * plan for the user. Returns the new plan id, or null if signed out.
     */
    suspend fun createPlan(name: String, planType: PlanType, makeActive: Boolean = true): String?

    suspend fun renamePlan(id: String, name: String)

    /** Make [id] the user's sole active plan (clears the flag on the others). */
    suspend fun setActivePlan(id: String)

    /** Soft-delete a plan AND all its days + planned exercises atomically (one tombstone clock). */
    suspend fun deletePlan(id: String)

    // ---- Days ----

    /** Live days of a plan, in slot order (empty when signed out or plan absent). */
    fun observeDays(planId: String): Flow<List<WorkoutDay>>

    suspend fun getDay(id: String): WorkoutDay?

    /** Add a day at [slotKey] to [planId]. Returns the new day id, or null if signed out. */
    suspend fun addDay(planId: String, slotKey: String, name: String): String?

    suspend fun renameDay(id: String, name: String)

    /** Soft-delete a day AND its planned exercises atomically. */
    suspend fun deleteDay(id: String)

    // ---- Planned exercises ----

    /**
     * Live planned exercises of a day joined with their catalog [com.hellohealth.domain.model.Exercise]
     * (in display order), so the Day screen has name/gif/loggingType without a second call. A planned
     * row whose catalog entry is missing still emits (exercise = null) rather than being dropped.
     */
    fun observePlannedExercises(dayId: String): Flow<List<PlannedExerciseWithDetails>>

    /**
     * A single planned exercise joined with its catalog [com.hellohealth.domain.model.Exercise], or
     * null if absent / signed out. Backs the target editor, which needs the current targets to seed
     * its form and the catalog details (gif / instructions / loggingType) to render.
     */
    suspend fun getPlannedExercise(id: String): PlannedExerciseWithDetails?

    /**
     * Append an exercise to a day with default targets (targetSets=3). [orderIndex] is assigned after
     * the current max. Returns the new planned-exercise id, or null if signed out.
     */
    suspend fun addExercise(dayId: String, exerciseId: String): String?

    /** Persist edited targets. The caller passes the full desired [PlannedExercise] (id must exist). */
    suspend fun updateTargets(planned: PlannedExercise)

    /** Reorder a day's planned exercises to match [orderedIds] (index = position). */
    suspend fun reorderExercises(dayId: String, orderedIds: List<String>)

    /** Soft-delete a single planned exercise. */
    suspend fun deleteExercise(id: String)
}
