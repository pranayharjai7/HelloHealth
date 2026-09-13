package com.hellohealth.domain.health

import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyEnergyTest {

    // Reference: 30yo male, 80 kg, 180 cm.
    // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5 = 1780
    @Test
    fun `bmr male matches Mifflin-St Jeor`() {
        val bmr = BodyEnergy.bmr(weightKg = 80.0, heightCm = 180.0, ageYears = 30, gender = Gender.MALE)
        assertEquals(1780.0, bmr!!, 0.001)
    }

    // Same body, female constant -161: 800 + 1125 - 150 - 161 = 1614
    @Test
    fun `bmr female matches Mifflin-St Jeor`() {
        val bmr = BodyEnergy.bmr(weightKg = 80.0, heightCm = 180.0, ageYears = 30, gender = Gender.FEMALE)
        assertEquals(1614.0, bmr!!, 0.001)
    }

    // Neutral fallback for unset/other = midpoint of +5 and -161 = -78: 800 + 1125 - 150 - 78 = 1697
    @Test
    fun `bmr uses neutral constant when sex is unset or other`() {
        val expected = 1697.0
        assertEquals(expected, BodyEnergy.bmr(80.0, 180.0, 30, null)!!, 0.001)
        assertEquals(expected, BodyEnergy.bmr(80.0, 180.0, 30, Gender.OTHER)!!, 0.001)
        assertEquals(expected, BodyEnergy.bmr(80.0, 180.0, 30, Gender.PREFER_NOT_TO_SAY)!!, 0.001)
    }

    @Test
    fun `bmr is null when a required input is missing`() {
        assertNull(BodyEnergy.bmr(null, 180.0, 30, Gender.MALE))
        assertNull(BodyEnergy.bmr(80.0, null, 30, Gender.MALE))
        assertNull(BodyEnergy.bmr(80.0, 180.0, null, Gender.MALE))
    }

    @Test
    fun `tdee applies the activity multiplier`() {
        // BMR 1780 * MODERATE 1.55 = 2759
        val tdee = BodyEnergy.tdee(80.0, 180.0, 30, Gender.MALE, ActivityLevel.MODERATE)
        assertEquals(2759.0, tdee!!, 0.001)
    }

    @Test
    fun `tdee falls back to sedentary when activity level is unset`() {
        // BMR 1780 * SEDENTARY 1.2 = 2136
        val tdee = BodyEnergy.tdee(80.0, 180.0, 30, Gender.MALE, null)
        assertEquals(2136.0, tdee!!, 0.001)
    }

    @Test
    fun `calorieBudget maintain equals tdee`() {
        val today = LocalDate.of(2026, 1, 1)
        val born = LocalDate.of(1996, 1, 1) // exactly 30
        val profile = UserProfile(
            gender = Gender.MALE,
            birthDateEpochDay = born.toEpochDay(),
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.MAINTAIN
        )
        // TDEE 2759 rounded
        assertEquals(2759, profile.let { BodyEnergy.calorieBudget(it, today) })
    }

    @Test
    fun `calorieBudget lose applies a deficit from the target rate`() {
        val today = LocalDate.of(2026, 1, 1)
        val born = LocalDate.of(1996, 1, 1)
        val profile = UserProfile(
            gender = Gender.MALE,
            birthDateEpochDay = born.toEpochDay(),
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE,
            targetRateKgPerWeek = 0.5
        )
        // TDEE 2759 - (0.5*7700/7 = 550) = 2209
        assertEquals(2209, BodyEnergy.calorieBudget(profile, today))
    }

    @Test
    fun `calorieBudget never drops below the 1200 floor`() {
        val today = LocalDate.of(2026, 1, 1)
        val born = LocalDate.of(1996, 1, 1)
        val profile = UserProfile(
            gender = Gender.FEMALE,
            birthDateEpochDay = born.toEpochDay(),
            heightCm = 150.0,
            weightKg = 45.0,
            activityLevel = ActivityLevel.SEDENTARY,
            goalType = GoalType.LOSE,
            targetRateKgPerWeek = 1.0 // aggressive deficit
        )
        assertTrue(BodyEnergy.calorieBudget(profile, today)!! >= 1200)
    }

    @Test
    fun `calorieBudget is null when vitals are missing`() {
        assertNull(BodyEnergy.calorieBudget(UserProfile(displayName = "x")))
    }

    @Test
    fun `suggestedGoals scale with activity level`() {
        val sedentary = BodyEnergy.suggestedGoals(UserProfile(activityLevel = ActivityLevel.SEDENTARY))
        val veryActive = BodyEnergy.suggestedGoals(UserProfile(activityLevel = ActivityLevel.VERY_ACTIVE))
        assertTrue(veryActive.steps > sedentary.steps)
        assertTrue(veryActive.activeMinutes > sedentary.activeMinutes)
    }
}
