package com.hellohealth.data.repository

import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * P0.5 Step 10d — the profile-derived BMR fallback that [ActivityRepositoryImpl] hands to
 * [com.hellohealth.data.health.HealthConnectManager.fetchHealthSummary] when Health Connect has no
 * basal-rate record. Verifies the source selection: a complete profile yields its Mifflin-St Jeor
 * BMR (neutral-sex when gender is unset); a null/incomplete profile yields the 1800.0 default.
 *
 * Pure JVM — [ActivityRepositoryImpl.profileBmrOrDefault] touches no Android APIs.
 */
class ActivityRepositoryBmrFallbackTest {

    /** A birth date giving a fixed age so the derived BMR is deterministic across run dates. */
    private fun birthEpochDayForAge(age: Int, today: LocalDate = LocalDate.now()): Long =
        today.minusYears(age.toLong()).toEpochDay()

    @Test
    fun `complete profile yields its Mifflin-St Jeor BMR, not the default`() {
        val profile = UserProfile(
            weightKg = 80.0,
            heightCm = 180.0,
            birthDateEpochDay = birthEpochDayForAge(30),
            gender = Gender.MALE
        )

        // 10*80 + 6.25*180 - 5*30 + 5 (MALE constant) = 1780.0
        val expected = BodyEnergy.bmr(80.0, 180.0, 30, Gender.MALE)!!
        assertEquals(1780.0, expected, 0.0001) // guards the formula itself
        assertEquals(expected, ActivityRepositoryImpl.profileBmrOrDefault(profile), 0.0001)
    }

    @Test
    fun `unset gender uses the neutral sex constant, still not the default`() {
        val profile = UserProfile(
            weightKg = 80.0,
            heightCm = 180.0,
            birthDateEpochDay = birthEpochDayForAge(30),
            gender = null
        )

        // Neutral constant (-78): 800 + 1125 - 150 - 78 = 1697.0
        assertEquals(1697.0, ActivityRepositoryImpl.profileBmrOrDefault(profile), 0.0001)
    }

    @Test
    fun `null profile falls back to the 1800 default`() {
        assertEquals(
            ActivityRepositoryImpl.DEFAULT_BMR,
            ActivityRepositoryImpl.profileBmrOrDefault(null),
            0.0001
        )
    }

    @Test
    fun `profile missing a vital (no birth date) falls back to the 1800 default`() {
        val profile = UserProfile(weightKg = 80.0, heightCm = 180.0, gender = Gender.MALE)
        assertEquals(
            ActivityRepositoryImpl.DEFAULT_BMR,
            ActivityRepositoryImpl.profileBmrOrDefault(profile),
            0.0001
        )
    }
}
