package com.hellohealth.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A shimmering placeholder block — an animated light sweep over a rounded surface — used to show that
 * content is LOADING (its shape is visible) rather than EMPTY. Distinguishing the two was the whole
 * point: cards used to render their "no data" state during the initial read, which read as "nothing
 * here" when data was in fact on its way.
 *
 * The sweep is a diagonal gradient translated left→right on an infinite transition. Colors derive from
 * the caller's [base] so it sits correctly on any surface in light and dark.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    base: Color = Color.Gray,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "shimmerX",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.10f), base.copy(alpha = 0.22f), base.copy(alpha = 0.10f)),
        start = Offset(x * 400f, 0f),
        end = Offset(x * 400f + 400f, 400f),
    )
    androidx.compose.foundation.layout.Box(modifier.clip(shape).background(brush))
}

/** A single shimmer bar of a given height/width fraction — convenience for skeleton rows. */
@Composable
fun ShimmerBar(heightDp: Dp, widthFraction: Float = 1f, base: Color = Color.Gray) {
    ShimmerBox(
        modifier = Modifier.fillMaxWidth(widthFraction).height(heightDp),
        base = base,
    )
}

/**
 * Skeleton for a score-style card (big number + label + a couple of supporting lines) — matches the
 * shape of the Wellness/Health cards so the transition to real content doesn't jump. Pass the card's
 * own [base] (usually `onSurface`) so the shimmer reads on its surface.
 */
@Composable
fun ScoreCardSkeleton(modifier: Modifier = Modifier, base: Color = Color.Gray) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShimmerBox(Modifier.size(56.dp), base = base, shape = CircleShape)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            ShimmerBar(heightDp = 14.dp, widthFraction = 0.35f, base = base)
            Spacer(Modifier.height(10.dp))
            ShimmerBar(heightDp = 28.dp, widthFraction = 0.55f, base = base)
            Spacer(Modifier.height(10.dp))
            ShimmerBar(heightDp = 12.dp, widthFraction = 0.7f, base = base)
        }
    }
}
