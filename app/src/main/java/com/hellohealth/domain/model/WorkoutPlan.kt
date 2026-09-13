package com.hellohealth.domain.model

/**
 * A training routine — the top of the workout-planning hierarchy
 * ([WorkoutPlan] → [WorkoutDay] → [PlannedExercise]).
 *
 * A user may have several plans but at most one [isActive] at a time (enforced by the repository,
 * not the schema, so the invariant is easy to relax later). [planType] fixes the routine's
 * scheduling shape and therefore which slot keys its days may occupy — see [PlanType.slotKeysFor].
 *
 * Timestamps are UTC epoch millis. [createdAt] is set once at creation; [updatedAt] is bumped on
 * every mutation and is the Last-Write-Wins clock the syncer compares against the server. Sync
 * bookkeeping (tz offset, tombstone, synced flag) lives on the entity, not this domain model.
 */
data class WorkoutPlan(
    val id: String,
    val userId: String,
    val name: String,
    val isActive: Boolean,
    val planType: PlanType,
    val createdAt: Long,
    val updatedAt: Long,
)
