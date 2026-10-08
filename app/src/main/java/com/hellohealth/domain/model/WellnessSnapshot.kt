package com.hellohealth.domain.model

import com.hellohealth.domain.wellness.Achievement
import com.hellohealth.domain.wellness.PillarScore
import com.hellohealth.domain.wellness.WellnessBand

/**
 * A computed wellness snapshot for a given day — the dashboard card + wellness screen read this.
 * [score] is null when no pillar was tracked that day (empty state, never a fake 0). [pillars] are
 * the present pillars with sub-scores for the breakdown. [streaks] are the current per-pillar +
 * balanced streaks; [totalPoints] is the lifetime ledger sum; [earnedAchievements] are the unlocked
 * ones (newest first).
 */
data class WellnessSnapshot(
    val localDate: String,
    val score: Int?,
    val band: WellnessBand?,
    val pillars: List<PillarScore>,
    val streaks: List<WellnessStreak>,
    val totalPoints: Int,
    val earnedAchievements: List<EarnedAchievement>,
)

/** A current streak for display: the pillar label + current/longest counts. */
data class WellnessStreak(
    val pillarKey: String,
    val label: String,
    val currentCount: Int,
    val longestCount: Int,
)

/** An unlocked achievement for display. */
data class EarnedAchievement(
    val achievement: Achievement,
    val unlockedAtEpochMs: Long,
)
