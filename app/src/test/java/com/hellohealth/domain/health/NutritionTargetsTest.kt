package com.hellohealth.domain.health

import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.model.nutrition.NutritionGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NutritionTargetsTest {

    private val today = LocalDate.of(2026, 1, 1)
    // 30yo male, 180cm, 80kg, MODERATE, MAINTAIN → budget 2759 (per BodyEnergyTest).
    private val profile = UserProfile(
        gender = Gender.MALE,
        birthDateEpochDay = LocalDate.of(1996, 1, 1).toEpochDay(),
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = ActivityLevel.MODERATE,
        goalType = GoalType.MAINTAIN,
    )

    @Test
    fun `auto mode uses the profile derivation`() {
        val r = NutritionTargets.resolve(profile, NutritionGoals.DEFAULT)
        assertEquals(2759, r.calorieBudget)
        // MAINTAIN 30/40/30: P 2759*.3/4=207, C 276, F 92
        assertEquals(207, r.proteinG)
        assertEquals(276, r.carbsG)
        assertEquals(92, r.fatG)
        assertEquals(MicronutrientTargets.fiberTargetG(2759), r.fiberG)
        assertEquals(MicronutrientTargets.Micro.entries.toSet(), r.trackedMicros)
    }

    @Test
    fun `custom calories override the derived budget and re-scale derived macros`() {
        val r = NutritionTargets.resolve(
            profile,
            NutritionGoals(useCustomCalories = true, calorieBudget = 2000),
        )
        assertEquals(2000, r.calorieBudget)
        // Macros still auto, but derived FROM the custom budget: 2000 MAINTAIN → P 150, C 200, F 67.
        assertEquals(150, r.proteinG)
        assertEquals(200, r.carbsG)
        assertEquals(67, r.fatG)
    }

    @Test
    fun `custom macros win when set`() {
        val r = NutritionTargets.resolve(
            profile,
            NutritionGoals(useCustomMacros = true, proteinG = 200, carbsG = 150, fatG = 60, fiberG = 40),
        )
        assertEquals(200, r.proteinG)
        assertEquals(150, r.carbsG)
        assertEquals(60, r.fatG)
        assertEquals(40, r.fiberG)
        // Calories still auto.
        assertEquals(2759, r.calorieBudget)
    }

    @Test
    fun `partial custom macro falls back to derivation for the missing one`() {
        val r = NutritionTargets.resolve(
            profile,
            NutritionGoals(useCustomMacros = true, proteinG = 220, carbsG = null, fatG = null, fiberG = null),
        )
        assertEquals("custom protein kept", 220, r.proteinG)
        assertEquals("missing carbs derived", 276, r.carbsG)   // derived from 2759
        assertEquals("missing fat derived", 92, r.fatG)
        assertEquals("missing fiber derived", MicronutrientTargets.fiberTargetG(2759), r.fiberG)
    }

    @Test
    fun `custom flag off ignores stored values`() {
        // Values present but flags off → derivation wins (user toggled back to auto).
        val r = NutritionTargets.resolve(
            profile,
            NutritionGoals(useCustomCalories = false, calorieBudget = 9999, useCustomMacros = false, proteinG = 999),
        )
        assertEquals(2759, r.calorieBudget)
        assertEquals(207, r.proteinG)
    }

    @Test
    fun `null profile yields null budget and macros but still a fiber DV and micro set`() {
        val r = NutritionTargets.resolve(null, NutritionGoals.DEFAULT)
        assertNull(r.calorieBudget)
        assertNull(r.proteinG)
        assertEquals(MicronutrientTargets.FIBER_DV_G, r.fiberG) // fiber falls back to the flat DV
    }

    @Test
    fun `tracked micros subset is carried through`() {
        val subset = setOf(MicronutrientTargets.Micro.IRON, MicronutrientTargets.Micro.VITAMIN_C)
        val r = NutritionTargets.resolve(profile, NutritionGoals(trackedMicros = subset))
        assertEquals(subset, r.trackedMicros)
    }

    @Test
    fun `grams-percent conversions round-trip`() {
        // 150g carbs at 4 kcal/g on a 2000 budget = 30%.
        assertEquals(30, NutritionTargets.gramsToPercent(150, NutritionTargets.KCAL_C, 2000))
        assertEquals(150, NutritionTargets.percentToGrams(30, NutritionTargets.KCAL_C, 2000))
    }
}
