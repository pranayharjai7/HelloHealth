package com.hellohealth.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicronutrientTargetsTest {

    @Test
    fun `fibre target scales with the calorie budget at 14g per 1000 kcal`() {
        assertEquals(28, MicronutrientTargets.fiberTargetG(2000)) // 2000 kcal → 28 g
        assertEquals(35, MicronutrientTargets.fiberTargetG(2500)) // 2500 kcal → 35 g
    }

    @Test
    fun `fibre target falls back to the 28g DV when no budget`() {
        assertEquals(MicronutrientTargets.FIBER_DV_G, MicronutrientTargets.fiberTargetG(null))
        assertEquals(MicronutrientTargets.FIBER_DV_G, MicronutrientTargets.fiberTargetG(0))
    }

    @Test
    fun `micronutrient targets match the FDA Daily Values`() {
        assertEquals(2300.0, MicronutrientTargets.targetFor(MicronutrientTargets.Micro.SODIUM), 0.001)
        assertEquals(1300.0, MicronutrientTargets.targetFor(MicronutrientTargets.Micro.CALCIUM), 0.001)
        assertEquals(4700.0, MicronutrientTargets.targetFor(MicronutrientTargets.Micro.POTASSIUM), 0.001)
        assertEquals(20.0, MicronutrientTargets.targetFor(MicronutrientTargets.Micro.VITAMIN_D), 0.001)
        assertEquals(90.0, MicronutrientTargets.targetFor(MicronutrientTargets.Micro.VITAMIN_C), 0.001)
    }

    @Test
    fun `iron target splits by need`() {
        assertEquals(18.0, MicronutrientTargets.targetFor(MicronutrientTargets.Micro.IRON, ironIsHigherNeed = true), 0.001)
        assertEquals(8.0, MicronutrientTargets.targetFor(MicronutrientTargets.Micro.IRON, ironIsHigherNeed = false), 0.001)
    }

    @Test
    fun `limit nutrients are flagged as ceilings`() {
        assertTrue(MicronutrientTargets.Micro.SODIUM.isLimit)
        assertTrue(MicronutrientTargets.Micro.SATURATED_FAT.isLimit)
        assertTrue(MicronutrientTargets.Micro.SUGAR.isLimit)
        assertTrue(!MicronutrientTargets.Micro.CALCIUM.isLimit)
        assertTrue(!MicronutrientTargets.Micro.IRON.isLimit)
    }
}
