package com.hellohealth.domain.model

/**
 * A logged workout session — the ACTUALS of F1 live logging, distinct from the [WorkoutPlan]
 * planning hierarchy. Its sets are [SessionSet]s.
 *
 * [planId]/[dayId] optionally anchor the session to the planned day it was started from (both null
 * for an ad-hoc session). [status] is one of [SessionStatus]. A user has at most one [active]
 * session at a time (enforced by the repository). [localDate] buckets the session into a calendar
 * day for the date-aware Health screen. [totalVolumeKg]/[caloriesEstimate] are derived at finish
 * and null until then.
 *
 * Timestamps are UTC epoch millis. [updatedAt] is the LWW clock; sync bookkeeping lives on the
 * entity, not this domain model.
 */
data class WorkoutSession(
    val id: String,
    val userId: String,
    val planId: String?,
    val dayId: String?,
    val title: String?,
    val activityType: String,
    val startEpochMs: Long,
    val endEpochMs: Long?,
    val durationSeconds: Int?,
    val status: SessionStatus,
    val localDate: String,
    val note: String?,
    val totalVolumeKg: Double?,
    val caloriesEstimate: Double?,
    val updatedAt: Long,
) {
    val isActive: Boolean get() = status == SessionStatus.ACTIVE
}

/** Lifecycle of a [WorkoutSession]. Stored as the lowercase [wire] value in Room/Supabase. */
enum class SessionStatus(val wire: String) {
    ACTIVE("active"),
    COMPLETED("completed"),
    ABANDONED("abandoned");

    companion object {
        /** Parse a stored wire value, defaulting to [ACTIVE] for an unknown/legacy value. */
        fun fromWire(value: String?): SessionStatus =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}
