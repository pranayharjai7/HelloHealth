package com.hellohealth.data.health

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * P0.5 Step 10d — [HealthConnectManager.selectBmr] precedence: the Health Connect basal-rate record
 * wins when present and positive; otherwise the lazily-supplied fallback (profile-derived → 1800.0)
 * is invoked. Pure JVM — no Health Connect client or Android APIs involved.
 */
class HealthConnectManagerBmrSelectionTest {

    @Test
    fun `HC record wins over the fallback when present and positive`() = runTest {
        assertEquals(1650.0, HealthConnectManager.selectBmr(hcBmr = 1650.0) { 1780.0 }, 0.0001)
    }

    @Test
    fun `HC record present means the fallback supplier is never invoked`() = runTest {
        var invoked = false
        val bmr = HealthConnectManager.selectBmr(hcBmr = 1650.0) { invoked = true; 1780.0 }
        assertEquals(1650.0, bmr, 0.0001)
        assertFalse("fallback must be lazy — not read when the HC record is usable", invoked)
    }

    @Test
    fun `missing HC record invokes and uses the fallback supplier`() = runTest {
        var invoked = false
        val bmr = HealthConnectManager.selectBmr(hcBmr = null) { invoked = true; 1780.0 }
        assertEquals(1780.0, bmr, 0.0001)
        assertEquals("fallback must be invoked when the HC record is absent", true, invoked)
    }

    @Test
    fun `non-positive HC record is ignored in favor of the fallback`() = runTest {
        assertEquals(1780.0, HealthConnectManager.selectBmr(hcBmr = 0.0) { 1780.0 }, 0.0001)
        assertEquals(1780.0, HealthConnectManager.selectBmr(hcBmr = -50.0) { 1780.0 }, 0.0001)
    }
}
