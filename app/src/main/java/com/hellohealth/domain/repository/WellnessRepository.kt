package com.hellohealth.domain.repository

import com.hellohealth.domain.model.WellnessSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Owns the wellness score + gamification state (streaks, points, achievements). Recomputes the daily
 * score live from the four pillars (activity / nutrition / recovery / mood) via the pure engines, and
 * persists streak/ledger/achievement rows ONLY when the observed day is today (past-day views are
 * read-only — browsing history never writes). Offline-first; signed-out emits an empty snapshot.
 *
 * Idempotency: the points ledger is keyed per (day, source) so recompute never double-awards; an
 * achievement's unlock timestamp is set once and never overwritten.
 */
interface WellnessRepository {

    /**
     * Observe the wellness snapshot as of [date]. The score + pillar breakdown are always computed
     * live. When [date] is today, the current streaks / points / achievements are also recomputed and
     * persisted; for a past [date] the gamification state reflects what's already stored (read-only).
     */
    fun observeSnapshot(date: LocalDate): Flow<WellnessSnapshot>
}
