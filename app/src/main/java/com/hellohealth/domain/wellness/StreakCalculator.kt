package com.hellohealth.domain.wellness

import java.time.LocalDate

/**
 * Pure streak math. A pillar is "hit" on a day when its daily record meets that pillar's bar (the
 * caller decides the bar and passes a set of hit-days). The streak is the run of consecutive days
 * ending at [asOf] — the same `asReversed().takeWhile{}` walk-back idiom as
 * [com.hellohealth.domain.usecase.BuildWeeklyInsightsUseCase]'s step streak, generalized per pillar
 * and for a composite "Balanced Day" (every tracked pillar hit).
 *
 * No I/O, no clock; the caller supplies [asOf] and the per-day hit sets.
 */
class StreakCalculator {

    /**
     * The current streak for one pillar: consecutive days up to and including [asOf] whose date is in
     * [hitDays]. A gap on [asOf] itself yields 0.
     */
    fun currentStreak(hitDays: Set<LocalDate>, asOf: LocalDate): Int {
        var day = asOf
        var count = 0
        while (day in hitDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /**
     * The current "Balanced Day" streak: consecutive days up to [asOf] on which EVERY pillar tracked
     * that day was also hit that day. [trackedByDay]/[hitByDay] map a date to the pillars tracked /
     * hit; a day with no tracked pillars breaks the streak (nothing to balance).
     */
    fun balancedDayStreak(
        trackedByDay: Map<LocalDate, Set<Pillar>>,
        hitByDay: Map<LocalDate, Set<Pillar>>,
        asOf: LocalDate,
    ): Int {
        var day = asOf
        var count = 0
        while (true) {
            val tracked = trackedByDay[day].orEmpty()
            val hit = hitByDay[day].orEmpty()
            if (tracked.isEmpty() || !hit.containsAll(tracked)) break
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
