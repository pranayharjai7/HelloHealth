package com.hellohealth.domain.wellness

import com.hellohealth.domain.wellness.AchievementEvaluator.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure [AchievementEvaluator]: each rule fires exactly at its threshold, and the earned
 * set is monotonic in progress (the "unlock guard" is the caller's job, but the evaluator must be a
 * stable pure function so recompute-on-read never flaps).
 */
class AchievementEvaluatorTest {

    private val evaluator = AchievementEvaluator()

    @Test
    fun `no progress earns nothing`() {
        assertTrue(evaluator.earned(Progress()).isEmpty())
    }

    @Test
    fun `first workout unlocks at exactly one`() {
        assertFalse(Achievement.FIRST_WORKOUT in evaluator.earned(Progress(totalWorkoutsLogged = 0)))
        assertTrue(Achievement.FIRST_WORKOUT in evaluator.earned(Progress(totalWorkoutsLogged = 1)))
    }

    @Test
    fun `ten workouts unlocks at the threshold and keeps first workout`() {
        val earned = evaluator.earned(Progress(totalWorkoutsLogged = 10))
        assertTrue(Achievement.FIRST_WORKOUT in earned)
        assertTrue(Achievement.TEN_WORKOUTS in earned)
    }

    @Test
    fun `balanced week needs a 7-day balanced streak`() {
        assertFalse(Achievement.BALANCED_WEEK in evaluator.earned(Progress(longestBalancedStreak = 6)))
        assertTrue(Achievement.BALANCED_WEEK in evaluator.earned(Progress(longestBalancedStreak = 7)))
    }

    @Test
    fun `activity fortnight needs a 14-day activity streak`() {
        assertFalse(Achievement.ACTIVITY_FORTNIGHT in evaluator.earned(Progress(longestActivityStreak = 13)))
        assertTrue(Achievement.ACTIVITY_FORTNIGHT in evaluator.earned(Progress(longestActivityStreak = 14)))
    }

    @Test
    fun `peak wellness needs a score of 90`() {
        assertFalse(Achievement.PEAK_WELLNESS in evaluator.earned(Progress(bestWellnessScore = 89)))
        assertTrue(Achievement.PEAK_WELLNESS in evaluator.earned(Progress(bestWellnessScore = 90)))
    }

    @Test
    fun `the earned set is exactly the rules whose thresholds are met`() {
        val earned = evaluator.earned(
            Progress(
                longestBalancedStreak = 7,
                longestActivityStreak = 3,
                totalWorkoutsLogged = 12,
                bestWellnessScore = 50,
            )
        )
        assertEquals(
            setOf(Achievement.FIRST_WORKOUT, Achievement.TEN_WORKOUTS, Achievement.BALANCED_WEEK),
            earned,
        )
    }
}
