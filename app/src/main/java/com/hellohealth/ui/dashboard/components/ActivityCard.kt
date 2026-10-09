@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.hellohealth.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.ui.navigation.LocalActivityAnimatedVisibilityScope
import com.hellohealth.ui.navigation.LocalSharedTransitionScope
import com.hellohealth.ui.navigation.activitySharedBounds

private const val DASH = "—"

/**
 * The shared Activity visualization — three concentric rings (steps / active-burn / active-time) with
 * a value legend. Used BOTH as the body of the dashboard [HealthCard] and as the Health screen's
 * Activity section, so the two always match (and so Stage 6 can morph one into the other with a
 * shared element). Stateless: fed already-resolved values + goals. Values dash when zero/absent —
 * never a fake 0 inside the ring (which also fixes the old overlap: numbers live in the legend, not
 * centered over the ring).
 */
@Composable
fun ActivityCard(
    steps: Long,
    goalSteps: Long,
    activeCalories: Double,
    goalActiveCalories: Int,
    activeMinutes: Long,
    goalActiveMinutes: Int,
    modifier: Modifier = Modifier,
    ringSize: androidx.compose.ui.unit.Dp = 120.dp,
) {
    // When rendered inside the shared-transition host (dashboard + Health screen), morph between the
    // two call sites; otherwise this is a no-op and the card renders normally.
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalActivityAnimatedVisibilityScope.current
    Row(
        modifier = modifier.activitySharedBounds(sharedScope, visibilityScope),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MultiActivityRings(
            stepsProgress = ratio(steps.toDouble(), goalSteps.toDouble()),
            caloriesProgress = ratio(activeCalories, goalActiveCalories.toDouble()),
            minutesProgress = ratio(activeMinutes.toDouble(), goalActiveMinutes.toDouble()),
            modifier = Modifier.size(ringSize),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityLegend("Steps", if (steps > 0) "$steps" else DASH, ActivityRingColors.steps)
            ActivityLegend("Active burn", if (activeCalories > 0) "${activeCalories.toInt()} Cal" else DASH, ActivityRingColors.calories)
            ActivityLegend("Active time", if (activeMinutes > 0) "$activeMinutes min" else DASH, ActivityRingColors.minutes)
        }
    }
}

@Composable
private fun ActivityLegend(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/** Progress ratio clamped to the ring's visible range (min 0.01 so an empty ring still shows a cap). */
private fun ratio(value: Double, goal: Double): Float =
    if (goal > 0) (value / goal).toFloat().coerceIn(0.01f, 1f) else 0.01f
