package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.WorkoutDay
import java.time.LocalDate
import javax.inject.Inject

/**
 * Resolves which planned [WorkoutDay] the dashboard "Smart Start" button should offer to start today,
 * for the active routine. PURE: no I/O and no `LocalDate.now()` inside — the caller passes [today], so
 * this is fully unit-testable and deterministic.
 *
 * Rules per [PlanType]:
 *  - WEEKLY  → the day whose slot matches today's weekday (`slotKey == today.dayOfWeek.name`, which is
 *    exactly the uppercase form WEEKLY slots use). No match (no day scheduled today) → null.
 *  - MONTHLY → the day whose slot matches today's day-of-month (`"D%02d".format(today.dayOfMonth)`).
 *    No match → null.
 *  - CUSTOM  → next-in-sequence: order the days by their canonical slot order, find the most recently
 *    trained one, and return the NEXT day (wrapping to the first). With no training history yet, start
 *    at the first day. (CUSTOM has no calendar meaning, so we advance through the routine in order.)
 *
 * Returns null when the plan has no days, or (WEEKLY/MONTHLY) when nothing is scheduled for today.
 */
class ResolveSmartStartDayUseCase @Inject constructor() {

    /**
     * @param planType the active plan's scheduling shape.
     * @param days the active plan's days (any order; this orders them canonically itself).
     * @param recentSessionDayIds planned-day ids of recent sessions, NEWEST FIRST (nulls/ad-hoc already
     *   filtered out by the caller). Only used for CUSTOM next-in-sequence.
     * @param today the local date to resolve against (caller supplies — keeps this pure).
     */
    operator fun invoke(
        planType: PlanType,
        days: List<WorkoutDay>,
        recentSessionDayIds: List<String>,
        today: LocalDate,
    ): WorkoutDay? {
        if (days.isEmpty()) return null

        return when (planType) {
            PlanType.WEEKLY -> days.firstOrNull { it.slotKey == today.dayOfWeek.name }
            PlanType.MONTHLY -> days.firstOrNull { it.slotKey == "D%02d".format(today.dayOfMonth) }
            PlanType.CUSTOM -> resolveCustomNext(days, recentSessionDayIds)
        }
    }

    /** Canonically-ordered CUSTOM days → the one after the most recently trained (wrap), else the first. */
    private fun resolveCustomNext(days: List<WorkoutDay>, recentSessionDayIds: List<String>): WorkoutDay {
        val order = PlanType.slotKeysFor(PlanType.CUSTOM)
        val ordered = days.sortedBy { order.indexOf(it.slotKey).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        val orderedIds = ordered.map { it.id }

        // The most recent session that targeted one of THIS plan's days defines "where we are".
        val lastTrainedId = recentSessionDayIds.firstOrNull { it in orderedIds }
        val lastIndex = orderedIds.indexOf(lastTrainedId) // -1 when nothing trained yet
        val nextIndex = (lastIndex + 1) % ordered.size      // -1 → 0 (first); wraps at the end
        return ordered[nextIndex]
    }
}
