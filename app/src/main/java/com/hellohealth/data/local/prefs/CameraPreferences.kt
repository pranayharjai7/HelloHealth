package com.hellohealth.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local persistence for the camera-capture UI preferences. This is intentionally NOT Room and
 * NOT synced to Supabase: the last-used camera lens is a per-device UI convenience, not user data —
 * it must survive sign-out and never round-trip to the server. Backed by a tiny [SharedPreferences]
 * file (framework built-in; no extra dependency). Constructor-injectable, so no Hilt @Provides needed.
 */
@Singleton
class CameraPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether the last-used lens was the front camera. Defaults to `true` on a fresh install so the
     * face-scan opens on the selfie camera (the original behavior).
     */
    var lastLensFront: Boolean
        get() = prefs.getBoolean(KEY_LENS_FRONT, true)
        set(value) { prefs.edit().putBoolean(KEY_LENS_FRONT, value).apply() }

    private companion object {
        const val PREFS_NAME = "camera_prefs"
        const val KEY_LENS_FRONT = "last_camera_lens_front"
    }
}
