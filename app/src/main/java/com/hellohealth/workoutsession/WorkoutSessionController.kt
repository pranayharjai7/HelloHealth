package com.hellohealth.workoutsession

import android.content.Context
import android.content.SharedPreferences
import com.hellohealth.core.time.Timestamps
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped holder of the active workout's REST TIMER — the one piece of live-logging state that
 * is neither in Room (it is ephemeral UI timing, not user data to sync) nor safe to keep purely
 * in-memory (a rest countdown must survive the app being killed and reopened mid-rest).
 *
 * The deadline [restEndsAtEpochMs] is the single source of truth, persisted to a tiny
 * [SharedPreferences] file (the same device-local convention as
 * [com.hellohealth.data.local.prefs.CameraPreferences]). "Remaining" is always derived as
 * `restEndsAtEpochMs − now` on read, never counted down in a stored field — so after process death
 * the UI reconstructs the exact remaining time by reading the deadline and subtracting the current
 * clock. A deadline already in the past reads as [RestState.Idle] (the rest is over).
 *
 * The ticking itself is the UI's concern (a composable `LaunchedEffect`); this controller only owns
 * the durable deadline and exposes it as a [StateFlow] so any observer (screen or foreground service)
 * sees the same value. In-memory StateFlow + durable SharedPreferences are kept in lockstep by
 * [startRest]/[clearRest]; [restoreFromDisk] re-seeds the flow from disk on construction so a fresh
 * process immediately reflects a rest that was running when the old process died.
 */
@Singleton
class WorkoutSessionController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _restState = MutableStateFlow<RestState>(RestState.Idle)

    /** The current rest-timer state. [RestState.Resting] carries the absolute deadline. */
    val restState: StateFlow<RestState> = _restState.asStateFlow()

    init {
        restoreFromDisk()
    }

    /**
     * Start (or restart) a rest countdown of [durationSeconds] from now. Persists the absolute
     * deadline so it survives process death. A non-positive duration clears the timer instead.
     */
    fun startRest(durationSeconds: Int) {
        if (durationSeconds <= 0) {
            clearRest()
            return
        }
        val endsAt = Timestamps.nowEpochMs() + durationSeconds * 1000L
        prefs.edit().putLong(KEY_REST_ENDS_AT, endsAt).apply()
        _restState.value = RestState.Resting(restEndsAtEpochMs = endsAt)
    }

    /** Stop the rest timer (skip / finished). Clears the durable deadline. */
    fun clearRest() {
        prefs.edit().remove(KEY_REST_ENDS_AT).apply()
        _restState.value = RestState.Idle
    }

    /**
     * Remaining rest in whole seconds right now (0 when idle or the deadline has passed). Derived
     * from the absolute deadline so it is always correct regardless of how long the process slept
     * or whether it was killed and restarted.
     */
    fun remainingSeconds(nowMs: Long = Timestamps.nowEpochMs()): Int {
        val endsAt = (restState.value as? RestState.Resting)?.restEndsAtEpochMs ?: return 0
        val remainingMs = endsAt - nowMs
        return if (remainingMs <= 0L) 0 else ((remainingMs + 999L) / 1000L).toInt()
    }

    /**
     * Re-seed the in-memory state from the persisted deadline. Called on construction (new process)
     * and safe to call again. A persisted deadline already in the past is treated as a finished rest:
     * the stale key is cleared and state is [RestState.Idle].
     */
    fun restoreFromDisk() {
        val endsAt = prefs.getLong(KEY_REST_ENDS_AT, 0L)
        _restState.value = if (endsAt > Timestamps.nowEpochMs()) {
            RestState.Resting(restEndsAtEpochMs = endsAt)
        } else {
            if (endsAt != 0L) prefs.edit().remove(KEY_REST_ENDS_AT).apply()
            RestState.Idle
        }
    }

    private companion object {
        const val PREFS_NAME = "workout_session_prefs"
        const val KEY_REST_ENDS_AT = "rest_ends_at_epoch_ms"
    }
}

/** The rest-timer state. [Resting] carries the absolute UTC-epoch-ms deadline, not a countdown. */
sealed interface RestState {
    data object Idle : RestState
    data class Resting(val restEndsAtEpochMs: Long) : RestState
}
