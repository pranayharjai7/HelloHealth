package com.hellohealth.domain.coaching

import com.hellohealth.domain.model.coaching.CoachingContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for [CoachingPrompt] — the rule-based coach's salience ordering and the context block
 * rendering. No Android/network here; this is the deterministic heart of the offline coach, so it is
 * covered thoroughly. The rule-based coach must NEVER fabricate data and must degrade to a gentle
 * "start logging" nudge on an empty context.
 */
class CoachingPromptTest {

    private val empty = CoachingContext.EMPTY

    @Test
    fun `empty context yields a start-logging nudge, not fabricated numbers`() {
        val text = CoachingPrompt.ruleBasedCoach(empty)
        assertTrue(text.contains("Log", ignoreCase = true))
        // No stray numbers implying data we don't have.
        assertFalse(text.any { it.isDigit() })
    }

    @Test
    fun `energy balance is the top salience signal`() {
        val ctx = empty.copy(netKcal = 400, calorieBudget = 2000, readinessScore = 90, waterMl = 2000, steps = 12000)
        val text = CoachingPrompt.ruleBasedCoach(ctx)
        assertTrue("should speak to the deficit first", text.contains("deficit", ignoreCase = true))
        assertTrue(text.contains("400"))
    }

    @Test
    fun `a large surplus is called out with a gentle nudge`() {
        val ctx = empty.copy(netKcal = -500, calorieBudget = 2000)
        val text = CoachingPrompt.ruleBasedCoach(ctx)
        assertTrue(text.contains("over your budget", ignoreCase = true))
        assertTrue(text.contains("500"))
    }

    @Test
    fun `recovery is used when no energy balance is available`() {
        val low = CoachingPrompt.ruleBasedCoach(empty.copy(readinessScore = 40))
        assertTrue(low.contains("lower", ignoreCase = true))
        val high = CoachingPrompt.ruleBasedCoach(empty.copy(readinessScore = 85))
        assertTrue(high.contains("strong", ignoreCase = true))
    }

    @Test
    fun `hydration is used when only water is present`() {
        val text = CoachingPrompt.ruleBasedCoach(empty.copy(waterMl = 500))
        assertTrue(text.contains("500"))
        assertTrue(text.contains("water", ignoreCase = true))
    }

    @Test
    fun `mood is used when only mood is present`() {
        val text = CoachingPrompt.ruleBasedCoach(empty.copy(dominantMoodToday = "HAPPINESS", moodCountToday = 2))
        assertTrue(text.contains("happiness", ignoreCase = true))
    }

    @Test
    fun `steps is used when only steps are present`() {
        val busy = CoachingPrompt.ruleBasedCoach(empty.copy(steps = 9000))
        assertTrue(busy.contains("9000"))
        val quiet = CoachingPrompt.ruleBasedCoach(empty.copy(steps = 1200))
        assertTrue(quiet.contains("1200"))
        assertTrue(quiet.contains("walk", ignoreCase = true))
    }

    @Test
    fun `context block omits absent fields and never shows a bare zero`() {
        val ctx = empty.copy(caloriesConsumed = 1500, waterMl = 750)
        val block = CoachingPrompt.contextBlock(ctx)
        assertTrue(block.contains("Calories consumed: 1500 kcal"))
        assertTrue(block.contains("Water: 750 ml"))
        // Fields we didn't set must not appear at all.
        assertFalse(block.contains("Steps"))
        assertFalse(block.contains("Recovery"))
    }

    @Test
    fun `context block renders net as a deficit or surplus`() {
        assertTrue(CoachingPrompt.contextBlock(empty.copy(netKcal = 300)).contains("300 kcal deficit"))
        assertTrue(CoachingPrompt.contextBlock(empty.copy(netKcal = -300)).contains("300 kcal surplus"))
    }

    @Test
    fun `empty context block says nothing logged`() {
        assertEquals("No data logged yet today.", CoachingPrompt.contextBlock(empty))
    }

    @Test
    fun `system prompt forbids medical advice and fabricated data`() {
        val sys = CoachingPrompt.systemPrompt()
        assertTrue(sys.contains("NOT a doctor", ignoreCase = true))
        assertTrue(sys.contains("Never invent data", ignoreCase = true))
    }
}
