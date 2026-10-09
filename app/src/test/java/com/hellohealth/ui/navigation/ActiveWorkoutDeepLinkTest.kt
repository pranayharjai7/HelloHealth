package com.hellohealth.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the ActiveWorkout deep-link contract shared by [WorkoutSessionService]'s notification intent
 * and the NavHost's registered [navDeepLink]. Both derive from [Screen.ActiveWorkout]; if the pattern
 * and the concrete URI drift apart the notification tap silently fails to resolve, so this guards the
 * one string they must agree on. Pure — no Android.
 */
class ActiveWorkoutDeepLinkTest {

    @Test
    fun `concrete deep-link uri matches the registered pattern scheme and host`() {
        val pattern = Screen.ActiveWorkout.DEEP_LINK      // hellohealth://active_workout?dayId={dayId}
        val uri = Screen.ActiveWorkout.deepLinkUri()      // hellohealth://active_workout?dayId=none

        val base = "hellohealth://active_workout"
        assertTrue("pattern uses the shared scheme+host", pattern.startsWith(base))
        assertTrue("concrete uri uses the shared scheme+host", uri.startsWith(base))
    }

    @Test
    fun `deepLinkUri defaults to the no-day sentinel so it re-attaches to the live session`() {
        assertEquals("hellohealth://active_workout?dayId=none", Screen.ActiveWorkout.deepLinkUri())
    }

    @Test
    fun `deepLinkUri carries a provided dayId`() {
        assertEquals("hellohealth://active_workout?dayId=day42", Screen.ActiveWorkout.deepLinkUri("day42"))
    }

    @Test
    fun `the deep-link pattern's dayId placeholder matches the route arg name`() {
        // The pattern's {dayId} must equal Screen.ActiveWorkout.dayIdArg so SavedStateHandle resolves it.
        assertTrue(Screen.ActiveWorkout.DEEP_LINK.contains("{${Screen.ActiveWorkout.dayIdArg}}"))
    }
}
