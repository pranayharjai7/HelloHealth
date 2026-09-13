package com.hellohealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UserProfileTest {

    @Test
    fun `ageYears computes whole years before the birthday this year`() {
        val born = LocalDate.of(1990, 6, 15)
        val today = LocalDate.of(2026, 6, 14) // day before the birthday
        val profile = UserProfile(birthDateEpochDay = born.toEpochDay())
        assertEquals(35, profile.ageYears(today))
    }

    @Test
    fun `ageYears computes whole years on and after the birthday`() {
        val born = LocalDate.of(1990, 6, 15)
        val today = LocalDate.of(2026, 6, 15) // the birthday itself
        val profile = UserProfile(birthDateEpochDay = born.toEpochDay())
        assertEquals(36, profile.ageYears(today))
    }

    @Test
    fun `ageYears is null when birth date is unset`() {
        assertNull(UserProfile().ageYears(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `ageYears is null for a future birth date rather than negative`() {
        val born = LocalDate.of(2030, 1, 1)
        val profile = UserProfile(birthDateEpochDay = born.toEpochDay())
        assertNull(profile.ageYears(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `defaults are pre-onboarding safe`() {
        val profile = UserProfile()
        assertEquals(UnitPreference.METRIC, profile.unitPreference)
        assertEquals(false, profile.hasOnboarded)
        assertNull(profile.gender)
        assertNull(profile.heightCm)
    }
}
