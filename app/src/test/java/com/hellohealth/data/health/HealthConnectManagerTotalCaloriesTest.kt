package com.hellohealth.data.health

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 1 Stage 1 — [HealthConnectManager.resolveTotalCalories]: the Health Connect
 * `TotalCaloriesBurnedRecord` aggregate wins when present; otherwise total burn is reconstructed as
 * active + BMR (resting burn included), never a bare fall-back to active-only. Pure JVM.
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
}
