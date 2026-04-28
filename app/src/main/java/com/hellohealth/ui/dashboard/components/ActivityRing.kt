package com.hellohealth.ui.dashboard.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ActivityRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    backgroundColor: Color = color.copy(alpha = 0.15f)
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress.coerceIn(0.01f, 1f), // Ensure at least a tiny bit is shown
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val size = this.size
        val strokeWidthPx = strokeWidth.toPx()
        val radius = (size.minDimension - strokeWidthPx) / 2
        
        // Background track
        drawCircle(
            color = backgroundColor,
            radius = radius,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
        
        // Progress arc
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360 * animatedProgress.value,
            useCenter = false,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun MultiActivityRings(
    stepsProgress: Float,
    caloriesProgress: Float,
    minutesProgress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val ringSpacing = 4.dp
        val strokeWidth = 16.dp
        
        // Steps Ring (Outer)
        ActivityRing(
            progress = stepsProgress,
            color = Color(0xFF4CAF50),
            modifier = Modifier.matchParentSize(),
            strokeWidth = strokeWidth
        )
        
        // Calories Ring (Middle)
        ActivityRing(
            progress = caloriesProgress,
            color = Color(0xFFFF7043),
            modifier = Modifier
                .fillMaxSize(0.75f)
                .padding(ringSpacing),
            strokeWidth = strokeWidth
        )
        
        // Active Minutes Ring (Inner)
        ActivityRing(
            progress = minutesProgress,
            color = Color(0xFF42A5F5),
            modifier = Modifier
                .fillMaxSize(0.5f)
                .padding(ringSpacing * 2),
            strokeWidth = strokeWidth
        )
    }
}
