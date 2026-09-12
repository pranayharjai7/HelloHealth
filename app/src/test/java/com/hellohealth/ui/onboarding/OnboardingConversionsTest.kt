package com.hellohealth.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit-conversion math for the Body step (Step 7). Storage is always metric; these guard the
 * imperial display/parse path. The [cmToFeetInches] boundary cases are the ones an adversarial
 * review flagged: rounding must never surface a nonsensical "12 in" component.
 */
class OnboardingConversionsTest {

    @Test
    fun `feet and inches convert to cm`() {
        // 5 ft 10 in = 70 in * 2.54 = 177.8 cm
        assertEquals(177.8, feetInchesToCm(5, 10.0), 0.001)
        // 6 ft 0 in = 72 in * 2.54 = 182.88 cm
        assertEquals(182.88, feetInchesToCm(6, 0.0), 0.001)
    }

    @Test
    fun `cm decomposes into a consistent feet-inches pair`() {
        val (feet, inches) = cmToFeetInches(177.8)
        assertEquals(5, feet)
        assertEquals(10.0, inches, 0.05)
    }

    @Test
    fun `inches component is always below twelve`() {
        // Sweep a dense range of plausible heights; the inches part must never reach 12
        // (the off-by-one the review confirmed). Step by a hundredth of a cm.
        var cm = 50.0
        while (cm <= 272.0) {
            val (feet, inches) = cmToFeetInches(cm)
            assertTrue("inches must be in [0,12) but was $inches at $cm cm", inches in 0.0..11.999999)
            assertTrue("feet must be non-negative at $cm cm", feet >= 0)
            cm += 0.01
        }
    }

    @Test
    fun `rounding boundary carries into feet instead of showing twelve inches`() {
        // A value whose (cm/2.54) inches-remainder rounds up to 12.0 must roll over to the next foot.
        val (feet, inches) = cmToFeetInches(182.754)
        assertEquals("should carry to 6 ft, not 5 ft 12 in", 6, feet)
        assertEquals(0.0, inches, 0.05)
    }

    @Test
    fun `kg and lb convert both directions`() {
        assertEquals(154.32, 70.0.kgToLb(), 0.01)
        assertEquals(70.0, 154.32.lbToKg(), 0.01)
        // Round-trip is lossless within tolerance.
        assertEquals(80.0, 80.0.kgToLb().lbToKg(), 0.0001)
    }
}
