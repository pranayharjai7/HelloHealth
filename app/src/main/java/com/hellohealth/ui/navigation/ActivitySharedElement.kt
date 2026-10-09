@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.hellohealth.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Shared-element plumbing for the dashboard Activity card ⇄ Health screen Activity section morph.
 *
 * Rather than thread [SharedTransitionScope] + [AnimatedVisibilityScope] through two deep screen
 * signatures (and [com.hellohealth.ui.dashboard.components.HealthCard]'s many siblings), we provide
 * them via CompositionLocals: [AppNavigation] wraps the NavHost in a `SharedTransitionLayout` and
 * supplies the scope once; each destination's `composable { }` lambda supplies ITS own
 * [AnimatedVisibilityScope]. The single consumer ([ActivityCard]) reads both and, only when both are
 * present, applies the shared-bounds modifier. Defaults are null so previews, tests, and any screen
 * rendered outside a transition host are entirely unaffected.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalActivityAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Stable key for the Activity rings/card shared element (same string at both ends of the morph). */
const val ACTIVITY_SHARED_ELEMENT_KEY = "activity-card"

/**
 * Returns [modifier] with a `sharedBounds` applied when BOTH scopes are available in the current
 * composition (i.e. we're inside the transition host and an active destination), otherwise returns
 * [modifier] unchanged. Centralizes the `@ExperimentalSharedTransitionApi` opt-in so call sites stay
 * clean and null-safe.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.activitySharedBounds(
    sharedScope: SharedTransitionScope?,
    visibilityScope: AnimatedVisibilityScope?,
): Modifier {
    if (sharedScope == null || visibilityScope == null) return this
    return this.composed {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = ACTIVITY_SHARED_ELEMENT_KEY),
                animatedVisibilityScope = visibilityScope,
            )
        }
    }
}
