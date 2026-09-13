package com.hellohealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [PlanType.slotKeysFor] (the canonical slot vocabulary that drives the "add day" picker and
 * day ordering) and [PlanType.fromName] (the defensive decode that keeps a corrupt/forward-version
 * stored value from crashing). Slot keys are opaque, stable Strings persisted on
 * [WorkoutDay.slotKey], so their exact form is a compatibility contract worth pinning.
 */
class PlanTypeTest {

    @Test
    fun `weekly slots are the seven weekdays in order`() {
        assertEquals(
            listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
            PlanType.slotKeysFor(PlanType.WEEKLY)
        )
    }

    @Test
    fun `monthly slots are D01 through D31 zero-padded`() {
        val slots = PlanType.slotKeysFor(PlanType.MONTHLY)
        assertEquals(31, slots.size)
        assertEquals("D01", slots.first())
        assertEquals("D31", slots.last())
    }

    @Test
    fun `custom slots are C01 through C99 zero-padded`() {
        val slots = PlanType.slotKeysFor(PlanType.CUSTOM)
        assertEquals(99, slots.size)
        assertEquals("C01", slots.first())
        assertEquals("C99", slots.last())
    }

    @Test
    fun `slot keys are unique within a plan type`() {
        PlanType.entries.forEach { type ->
            val slots = PlanType.slotKeysFor(type)
            assertEquals("slot keys for $type must be unique", slots.size, slots.toSet().size)
        }
    }

    @Test
    fun `fromName decodes exact names case-insensitively`() {
        assertEquals(PlanType.WEEKLY, PlanType.fromName("WEEKLY"))
        assertEquals(PlanType.MONTHLY, PlanType.fromName("monthly"))
        assertEquals(PlanType.CUSTOM, PlanType.fromName("Custom"))
    }

    @Test
    fun `fromName falls back to WEEKLY for unknown or null`() {
        assertEquals(PlanType.WEEKLY, PlanType.fromName(null))
        assertEquals(PlanType.WEEKLY, PlanType.fromName(""))
        assertEquals(PlanType.WEEKLY, PlanType.fromName("QUARTERLY"))
    }

    @Test
    fun `round-trips through name`() {
        PlanType.entries.forEach { type ->
            assertEquals(type, PlanType.fromName(type.name))
        }
    }

    @Test
    fun `weekly day slot keys are consumable by a picker without collisions across types`() {
        // Sanity: no weekly key looks like a monthly/custom key, so the same slotKey column can hold
        // any plan type's keys unambiguously.
        val weekly = PlanType.slotKeysFor(PlanType.WEEKLY).toSet()
        val monthly = PlanType.slotKeysFor(PlanType.MONTHLY).toSet()
        val custom = PlanType.slotKeysFor(PlanType.CUSTOM).toSet()
        assertTrue((weekly intersect monthly).isEmpty())
        assertTrue((weekly intersect custom).isEmpty())
        assertTrue((monthly intersect custom).isEmpty())
    }
}
