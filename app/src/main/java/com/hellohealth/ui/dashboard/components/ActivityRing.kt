package com.hellohealth.ui.dashboard.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    backgroundColor: Color = color.copy(alpha = 0.2f),
    content: @Composable () -> Unit = {}
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val size = this.size
            val radius = (size.minDimension - strokeWidth.toPx()) / 2
            
            // Background track
            drawCircle(
                color = backgroundColor,
                radius = radius,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
            
            // Progress arc
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360 * animatedProgress.value,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun MultiActivityRings(
    stepsProgress: Float,
    caloriesProgress: Float,
    minutesProgress: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Steps Ring (Outer)
        ActivityRing(
            progress = stepsProgress,
            color = Color(0xFF4CAF50),
            modifier = Modifier.size(200.dp),
            strokeWidth = 16.dp
        )
        
        // Calories Ring (Middle)
        ActivityRing(
            progress = caloriesProgress,
            color = Color(0xFFFF5722),
            modifier = Modifier.size(160.dp).padding(4.dp),
            strokeWidth = 16.dp
        )
        
        // Active Minutes Ring (Inner)
        ActivityRing(
            progress = minutesProgress,
            color = Color(0xFF2196F3),
            modifier = Modifier.size(120.dp).padding(4.dp),
            strokeWidth = 16.dp
        )
    }
}

private fun Modifier.fillMaxSize() = this.then(Modifier.size(Dp.Unspecified)) // Helper since we're in a Box
