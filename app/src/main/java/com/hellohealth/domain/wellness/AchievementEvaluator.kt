package com.hellohealth.domain.wellness

/**
 * Pure achievement rules. Given a snapshot of the user's progress, returns which achievements are
 * currently EARNED. The caller is responsible for persistence + one-time unlock (comparing this
 * earned set against already-unlocked rows) — this evaluator is a pure function of the snapshot so it
 * is trivially testable and side-effect free.
 */
class AchievementEvaluator {

    /**
     * Progress snapshot the rules read. All counts are "best so far"; the evaluator never mutates it.
     * Kept deliberately small for launch — new fields slot in as new [Achievement]s are added.
     */
    data class Progress(
        val longestBalancedStreak: Int = 0,
        val longestActivityStreak: Int = 0,
        val totalWorkoutsLogged: Int = 0,
        val bestWellnessScore: Int = 0,
    )

    /** The set of achievements earned for this [progress]. */
    fun earned(progress: Progress): Set<Achievement> = Achievement.entries
        .filter { it.isearnedBy(progress) }
        .toSet()
}

/**
 * The launch achievement catalog. Each carries its unlock predicate so [AchievementEvaluator] stays a
 * one-liner and the rule lives next to the achievement it defines. Codes are stable (persisted).
 */
enum class Achievement(
    val code: String,
    val title: String,
    val description: String,
    val isearnedBy: (AchievementEvaluator.Progress) -> Boolean,
) {
    FIRST_WORKOUT(
        code = "first_workout",
        title = "First Rep",
        description = "Log your first workout",
        isearnedBy = { it.totalWorkoutsLogged >= 1 },
    ),
    TEN_WORKOUTS(
        code = "ten_workouts",
        title = "Getting Consistent",
        description = "Log 10 workouts",
        isearnedBy = { it.totalWorkoutsLogged >= 10 },
    ),
    BALANCED_WEEK(
        code = "balanced_week",
        title = "Balanced Week",
        description = "A 7-day balanced streak",
        isearnedBy = { it.longestBalancedStreak >= 7 },
    ),
    ACTIVITY_FORTNIGHT(
        code = "activity_fortnight",
        title = "Two Strong Weeks",
        description = "Hit your activity goal 14 days running",
        isearnedBy = { it.longestActivityStreak >= 14 },
    ),
    PEAK_WELLNESS(
        code = "peak_wellness",
        title = "Thriving",
        description = "Reach a wellness score of 90",
        isearnedBy = { it.bestWellnessScore >= 90 },
    ),
}
