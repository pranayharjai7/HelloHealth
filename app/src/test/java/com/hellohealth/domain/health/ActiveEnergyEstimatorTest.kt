package com.hellohealth.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for [ActiveEnergyEstimator] — the tiered, physiology-grounded active-calories estimate
 * used when a provider (e.g. Samsung Health) writes no ActiveCaloriesBurnedRecord.
 */
class ActiveEnergyEstimatorTest {

    @Test
    fun `MET from distance gives a real-world active burn for a day of walking`() {
        // The reported day: 9094 steps, 6.76 km, ~100 active min, ~75 kg; Samsung's active was 374.
        // ACSM walking: v = 6760/100 = 67.6 m/min; grossVO2 = 0.1*67.6 + 3.5 = 10.26; net = 6.76;
        // kcal/min = 6.76*75/1000*5 = 2.535; *100 ≈ 253 kcal. In the right order (walking-only; the
        // gap to 374 is non-step movement). Reconciled against total 1555, basal 1555*0.73 ≈ 1134 →
        // ceiling ≈ 421, so 253 passes through unclamped.
        val r = ActiveEnergyEstimator.estimate(
            steps = 9094, distanceMeters = 6760.0, activeMinutes = 100, bodyMassKg = 75.0,
            hcTotalKcal = 1555.0, bmrKcalPerDay = 1555.0, elapsedFractionOfDay = 0.73,
        )
        assertEquals("met_distance", r.method)
        assertTrue("active burn should be a sensible 200-420 kcal, got ${r.activeKcal}", r.activeKcal in 200.0..420.0)
    }

    @Test
    fun `reconciliation ceiling caps active at total minus elapsed basal`() {
        // Huge implied movement but the device only burned 1555 total with 1134 basal-so-far →
        // active cannot exceed ~421.
        val r = ActiveEnergyEstimator.estimate(
            steps = 50000, distanceMeters = 40000.0, activeMinutes = 300, bodyMassKg = 90.0,
            hcTotalKcal = 1555.0, bmrKcalPerDay = 1555.0, elapsedFractionOfDay = 0.73,
        )
        assertTrue("clamped to ceiling ~421, got ${r.activeKcal}", r.activeKcal <= 421.1)
    }

    @Test
    fun `step heuristic fallback when distance is absent`() {
        val r = ActiveEnergyEstimator.estimate(
            steps = 10000, distanceMeters = 0.0, activeMinutes = 0, bodyMassKg = 70.0,
            hcTotalKcal = null, bmrKcalPerDay = 1600.0, elapsedFractionOfDay = 1.0,
        )
        assertEquals("step_heuristic", r.method)
        assertTrue(r.isEstimate)
        assertEquals(400.0, r.activeKcal, 0.0001) // 10000 * 0.04 * (70/70)
    }

    @Test
    fun `no movement yields zero`() {
        val r = ActiveEnergyEstimator.estimate(
            steps = 0, distanceMeters = 0.0, activeMinutes = 0, bodyMassKg = 70.0,
            hcTotalKcal = 1600.0, bmrKcalPerDay = 1600.0, elapsedFractionOfDay = 1.0,
        )
        assertEquals(0.0, r.activeKcal, 0.0001)
        assertEquals("none", r.method)
    }

    @Test
    fun `unknown body mass falls back to default without crashing`() {
        val r = ActiveEnergyEstimator.estimate(
            steps = 5000, distanceMeters = 3500.0, activeMinutes = 50, bodyMassKg = 0.0,
            hcTotalKcal = null, bmrKcalPerDay = 1500.0, elapsedFractionOfDay = 1.0,
        )
        assertTrue("uses default 70kg mass → positive burn", r.activeKcal > 0.0)
    }

    @Test
    fun `running pace uses the steeper running equation`() {
        // v = 12000/60 = 200 m/min = 12 km/h (running). grossVO2 = 0.2*200 + 3.5 = 43.5; net = 40;
        // kcal/min = 40*70/1000*5 = 14; *60 = 840 (no total → no ceiling).
        val r = ActiveEnergyEstimator.estimate(
            steps = 12000, distanceMeters = 12000.0, activeMinutes = 60, bodyMassKg = 70.0,
            hcTotalKcal = null, bmrKcalPerDay = 1600.0, elapsedFractionOfDay = 1.0,
        )
        assertEquals(840.0, r.activeKcal, 1.0)
    }
}
