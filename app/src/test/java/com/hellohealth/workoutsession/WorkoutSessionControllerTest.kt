package com.hellohealth.workoutsession

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.core.time.Timestamps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [WorkoutSessionController]'s rest-timer durability. The behaviour that carries the risk is
 * process-death restore: the rest deadline lives in SharedPreferences, and a FRESH controller (= a new
 * process) must reconstruct the exact remaining time by reading the deadline and subtracting the
 * current clock — never a stored countdown. Robolectric gives a real SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSessionControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearPrefs() {
        context.getSharedPreferences("workout_session_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun newController() = WorkoutSessionController(context)

    @Test
    fun `a fresh controller with no persisted rest is idle`() {
        val controller = newController()
        assertEquals(RestState.Idle, controller.restState.value)
        assertEquals(0, controller.remainingSeconds())
    }

    @Test
    fun `startRest persists a deadline and exposes a resting state`() {
        val controller = newController()
        val before = Timestamps.nowEpochMs()

        controller.startRest(durationSeconds = 90)

        val state = controller.restState.value
        assertTrue(state is RestState.Resting)
        val endsAt = (state as RestState.Resting).restEndsAtEpochMs
        // Deadline is ~90s out (allow a wide band for slow CI).
        assertTrue(endsAt >= before + 90_000L)
        assertTrue(endsAt <= Timestamps.nowEpochMs() + 90_000L)
        // Remaining derived from the deadline is ~90s.
        assertTrue(controller.remainingSeconds() in 85..90)
    }

    @Test
    fun `a non-positive rest clears the timer`() {
        val controller = newController()
        controller.startRest(60)
        controller.startRest(0)
        assertEquals(RestState.Idle, controller.restState.value)
    }

    @Test
    fun `clearRest removes the durable deadline`() {
        val controller = newController()
        controller.startRest(60)
        controller.clearRest()

        assertEquals(RestState.Idle, controller.restState.value)
        // A fresh controller (new process) also sees no rest.
        assertEquals(RestState.Idle, newController().restState.value)
    }

    // ----------------------------------------------- Process-death restore

    @Test
    fun `a fresh controller restores a rest that was running when the old process died`() {
        // Old process started a 2-minute rest...
        newController().startRest(durationSeconds = 120)

        // ...process dies, a brand-new controller is constructed (new process).
        val restored = newController()

        val state = restored.restState.value
        assertTrue("rest is restored from disk", state is RestState.Resting)
        // Remaining is derived live, so it is close to the original 120s (a touch less).
        assertTrue(restored.remainingSeconds() in 110..120)
    }

    @Test
    fun `a rest whose deadline already passed restores as idle and clears the stale key`() {
        val prefs = context.getSharedPreferences("workout_session_prefs", Context.MODE_PRIVATE)
        // Seed a deadline 5 seconds in the PAST (as if the rest finished while the app was dead).
        prefs.edit().putLong("rest_ends_at_epoch_ms", Timestamps.nowEpochMs() - 5_000L).commit()

        val controller = newController()

        assertEquals(RestState.Idle, controller.restState.value)
        assertEquals(0, controller.remainingSeconds())
        // The stale key was cleared.
        assertFalse(prefs.contains("rest_ends_at_epoch_ms"))
    }

    @Test
    fun `remainingSeconds is derived from the deadline, not a stored countdown`() {
        val controller = newController()
        val now = Timestamps.nowEpochMs()
        controller.startRest(durationSeconds = 100)
        val endsAt = (controller.restState.value as RestState.Resting).restEndsAtEpochMs

        // Pretend 40s have elapsed by passing a later "now" — remaining must drop accordingly.
        assertEquals(60, controller.remainingSeconds(nowMs = endsAt - 60_000L))
        // And a now past the deadline yields 0, never negative.
        assertEquals(0, controller.remainingSeconds(nowMs = endsAt + 10_000L))
        assertTrue(now <= endsAt)
    }
}
