package com.hellohealth.core.date

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Verifies [SelectedDateHolder]: default is today, past dates are accepted, and future dates are
 * ignored (the defense-in-depth guard the whole date-propagation feature relies on).
 */
class SelectedDateHolderTest {

    @Test
    fun `default selected date is today`() {
        val holder = SelectedDateHolder()
        assertEquals(LocalDate.now(ZoneId.systemDefault()), holder.selectedDate.value)
    }

    @Test
    fun `set accepts a past date`() {
        val holder = SelectedDateHolder()
        val past = LocalDate.now(ZoneId.systemDefault()).minusDays(3)
        holder.set(past)
        assertEquals(past, holder.selectedDate.value)
    }

    @Test
    fun `set ignores a future date, keeping the previous value`() {
        val holder = SelectedDateHolder()
        val today = LocalDate.now(ZoneId.systemDefault())
        holder.set(today.plusDays(5))
        assertEquals(today, holder.selectedDate.value)
    }

    @Test
    fun `set to today is always allowed`() {
        val holder = SelectedDateHolder()
        holder.set(LocalDate.now(ZoneId.systemDefault()).minusDays(2))
        val today = LocalDate.now(ZoneId.systemDefault())
        holder.set(today)
        assertEquals(today, holder.selectedDate.value)
    }
}
