package com.hellohealth.core.logging

import android.util.Log
import com.hellohealth.BuildConfig

/**
 * Single logging entry point for the app. Wraps [android.util.Log] so that:
 *  - every log line carries a consistent, filterable [FeatureTag];
 *  - verbose/debug output is suppressed in release builds (only warn/error survive),
 *    keeping release logs quiet without sprinkling `if (BuildConfig.DEBUG)` everywhere.
 *
 * This replaces the previous pattern of silent `runCatching {}` swallows in the data
 * layer: failures should be logged here, never discarded.
 */
object AppLogger {

    fun d(tag: FeatureTag, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag.tag, message)
    }

    fun i(tag: FeatureTag, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag.tag, message)
    }

    fun w(tag: FeatureTag, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag.tag, message, throwable) else Log.w(tag.tag, message)
    }

    fun e(tag: FeatureTag, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag.tag, message, throwable) else Log.e(tag.tag, message)
    }
}
