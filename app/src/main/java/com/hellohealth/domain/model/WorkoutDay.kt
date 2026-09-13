package com.hellohealth.domain.model

/**
 * A single workout within a [WorkoutPlan] — the middle of the planning hierarchy
 * ([WorkoutPlan] → [WorkoutDay] → [PlannedExercise]).
 *
 * [slotKey] is an opaque, stable String locating this day within its plan's schedule; its allowed
 * values depend on the parent plan's [PlanType] (`MONDAY`..`SUNDAY` for WEEKLY, `D01`..`D31` for
 * MONTHLY, `C01`..`C99` for CUSTOM — see [PlanType.slotKeysFor]). Keeping the key opaque here means
 * a new plan type is purely additive: no column reshaping, no enum coupling in this model.
 *
 * [name] is the user-facing label (e.g. "Push Day", "Legs"). [updatedAt] is the UTC epoch-ms LWW
 * clock; other sync bookkeeping lives on the entity.
 */
data class WorkoutDay(
    val id: String,
    val planId: String,
    val userId: String,
    val slotKey: String,
    val name: String,
    val updatedAt: Long,
)
