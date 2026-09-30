package com.hellohealth.domain.health

import com.hellohealth.domain.model.GoalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroTargetsTest {

    // MAINTAIN split = 30% protein / 40% carbs / 30% fat, at 4/4/9 kcal/g.
    // 2000 kcal → P = 2000*0.30/4 = 150 g, C = 2000*0.40/4 = 200 g, F = 2000*0.30/9 = 66.67 → 67 g.
    @Test
    fun `maintain split uses the balanced ratio and Atwater factors`() {
        val split = MacroTargets.split(calorieBudget = 2000, goal = GoalType.MAINTAIN)!!
        assertEquals(150, split.proteinG)
        assertEquals(200, split.carbsG)
        assertEquals(67, split.fatG)
    }

    // LOSE favors protein: 40/30/30. 2000 → P = 200 g, C = 150 g, F = 66.67 → 67 g.
    @Test
    fun `lose split favors protein`() {
        val split = MacroTargets.split(calorieBudget = 2000, goal = GoalType.LOSE)!!
        assertEquals(200, split.proteinG)
        assertEquals(150, split.carbsG)
        assertEquals(67, split.fatG)
    }

    // GAIN favors carbs: 30/45/25. 2400 → P = 2400*0.30/4 = 180, C = 2400*0.45/4 = 270, F = 2400*0.25/9 = 66.67 → 67.
    @Test
    fun `gain split favors carbs`() {
        val split = MacroTargets.split(calorieBudget = 2400, goal = GoalType.GAIN)!!
        assertEquals(180, split.proteinG)
        assertEquals(270, split.carbsG)
        assertEquals(67, split.fatG)
    }

    @Test
    fun `null goal falls back to the maintain ratio`() {
        val split = MacroTargets.split(calorieBudget = 2000, goal = null)!!
        assertEquals(150, split.proteinG)
        assertEquals(200, split.carbsG)
        assertEquals(67, split.fatG)
    }

    @Test
    fun `null budget yields null split`() {
        assertNull(MacroTargets.split(calorieBudget = null, goal = GoalType.MAINTAIN))
    }

    @Test
    fun `non-positive budget yields null split`() {
        assertNull(MacroTargets.split(calorieBudget = 0, goal = GoalType.MAINTAIN))
        assertNull(MacroTargets.split(calorieBudget = -100, goal = GoalType.MAINTAIN))
    }
}
