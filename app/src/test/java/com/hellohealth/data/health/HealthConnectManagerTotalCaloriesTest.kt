package com.hellohealth.data.health

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 1 Stage 1 — [HealthConnectManager.resolveTotalCalories] + [resolveActiveCalories]: calorie
 * resolution for both provider shapes — those that write ActiveCaloriesBurned and those (e.g. Samsung
 * Health) that write only TotalCaloriesBurned. Pure JVM.
 */
class HealthConnectManagerTotalCaloriesTest {

    @Test
    fun `HC total wins when present`() {
        assertEquals(1476.0, HealthConnectManager.resolveTotalCalories(hcTotal = 1476.0, active = 374.0, bmr = 1102.0), 0.0001)
    }

    @Test
    fun `missing HC total reconstructs active plus BMR`() {
        // The reported bug case: active 374 + BMR ~1102 ≈ Samsung's 1476 total, NOT 374.
        assertEquals(374.0 + 1102.0, HealthConnectManager.resolveTotalCalories(hcTotal = null, active = 374.0, bmr = 1102.0), 0.0001)
    }

    @Test
    fun `zero-activity day falls back to BMR, not zero`() {
        assertEquals(1800.0, HealthConnectManager.resolveTotalCalories(hcTotal = null, active = 0.0, bmr = 1800.0), 0.0001)
    }

    @Test
    fun `HC total of zero is honored (not treated as missing)`() {
        // Distinct from BMR fallback: a present-but-zero aggregate is a real reading, kept as-is.
        assertEquals(0.0, HealthConnectManager.resolveTotalCalories(hcTotal = 0.0, active = 374.0, bmr = 1102.0), 0.0001)
    }

    // --- resolveActiveCalories ---

    @Test
    fun `active present is used verbatim (including a genuine zero)`() {
        assertEquals(374.0, HealthConnectManager.resolveActiveCalories(hcActive = 374.0, hcTotal = 1476.0, bmr = 1102.0), 0.0001)
        assertEquals(0.0, HealthConnectManager.resolveActiveCalories(hcActive = 0.0, hcTotal = 1476.0, bmr = 1102.0), 0.0001)
    }

    @Test
    fun `active absent but total present reconstructs total minus BMR`() {
        // The Samsung-Health shape: no ActiveCaloriesBurned record → derive 1476 − 1102 = 374.
        assertEquals(374.0, HealthConnectManager.resolveActiveCalories(hcActive = null, hcTotal = 1476.0, bmr = 1102.0), 0.0001)
    }

    @Test
    fun `active absent on a rest day clamps to zero, no phantom burn`() {
        // total ≈ BMR → total − BMR ≈ 0 (negative clamps to 0), guarding the old inflation bug.
        assertEquals(0.0, HealthConnectManager.resolveActiveCalories(hcActive = null, hcTotal = 1100.0, bmr = 1150.0), 0.0001)
    }

    @Test
    fun `active and total both absent is zero`() {
        assertEquals(0.0, HealthConnectManager.resolveActiveCalories(hcActive = null, hcTotal = null, bmr = 1800.0), 0.0001)
    }
}
