package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Pure unit tests for [ResolveSmartStartDayUseCase] — WEEKLY/MONTHLY date match + CUSTOM next-in-sequence. */
class ResolveSmartStartDayUseCaseTest {

    private val useCase = ResolveSmartStartDayUseCase()

    private fun day(id: String, slotKey: String) =
        WorkoutDay(id = id, planId = "p", userId = "u", slotKey = slotKey, name = id, updatedAt = 0L)

    // --- empty ---

    @Test
    fun `no days returns null for every plan type`() {
        val today = LocalDate.of(2026, 10, 9) // a Friday
        assertNull(useCase(PlanType.WEEKLY, emptyList(), emptyList(), today))
        assertNull(useCase(PlanType.MONTHLY, emptyList(), emptyList(), today))
        assertNull(useCase(PlanType.CUSTOM, emptyList(), emptyList(), today))
    }

    // --- WEEKLY ---

    @Test
    fun `weekly matches today's weekday`() {
        val friday = LocalDate.of(2026, 10, 9) // Friday
        val days = listOf(day("mon", "MONDAY"), day("fri", "FRIDAY"), day("sun", "SUNDAY"))
        assertEquals("fri", useCase(PlanType.WEEKLY, days, emptyList(), friday)?.id)
    }

    @Test
    fun `weekly with no day scheduled today returns null`() {
        val friday = LocalDate.of(2026, 10, 9)
        val days = listOf(day("mon", "MONDAY"), day("tue", "TUESDAY"))
        assertNull(useCase(PlanType.WEEKLY, days, emptyList(), friday))
    }

    // --- MONTHLY ---

    @Test
    fun `monthly matches today's day-of-month zero-padded`() {
        val ninth = LocalDate.of(2026, 10, 9) // day 9 -> "D09"
        val days = listOf(day("d01", "D01"), day("d09", "D09"), day("d15", "D15"))
        assertEquals("d09", useCase(PlanType.MONTHLY, days, emptyList(), ninth)?.id)
    }

    @Test
    fun `monthly with no day for today returns null`() {
        val ninth = LocalDate.of(2026, 10, 9)
        val days = listOf(day("d01", "D01"), day("d15", "D15"))
        assertNull(useCase(PlanType.MONTHLY, days, emptyList(), ninth))
    }

    // --- CUSTOM next-in-sequence ---

    @Test
    fun `custom with no history starts at the first day in slot order`() {
        val today = LocalDate.of(2026, 10, 9)
        // Deliberately out of slot order in the input list.
        val days = listOf(day("c3", "C03"), day("c1", "C01"), day("c2", "C02"))
        assertEquals("c1", useCase(PlanType.CUSTOM, days, emptyList(), today)?.id)
    }

    @Test
    fun `custom advances to the day after the most recently trained`() {
        val today = LocalDate.of(2026, 10, 9)
        val days = listOf(day("c1", "C01"), day("c2", "C02"), day("c3", "C03"))
        // Newest-first: last trained c1 -> next is c2.
        assertEquals("c2", useCase(PlanType.CUSTOM, days, listOf("c1", "c3"), today)?.id)
    }

    @Test
    fun `custom wraps to the first day after the last`() {
        val today = LocalDate.of(2026, 10, 9)
        val days = listOf(day("c1", "C01"), day("c2", "C02"), day("c3", "C03"))
        // Last trained is the final day c3 -> wrap to c1.
        assertEquals("c1", useCase(PlanType.CUSTOM, days, listOf("c3", "c2"), today)?.id)
    }

    @Test
    fun `custom ignores recent session ids not belonging to this plan`() {
        val today = LocalDate.of(2026, 10, 9)
        val days = listOf(day("c1", "C01"), day("c2", "C02"))
        // "other" is from a different plan; the first matching id is c2 -> next wraps to c1.
        assertEquals("c1", useCase(PlanType.CUSTOM, days, listOf("other", "c2"), today)?.id)
    }
}
