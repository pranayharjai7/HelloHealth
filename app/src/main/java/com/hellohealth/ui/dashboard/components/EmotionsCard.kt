package com.hellohealth.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.ui.theme.moodAccentFor

/** Fraction the mood-dot tint is lerped toward its emotion accent — mirrors Theme's MOOD_TINT_FRACTION. */
private const val DOT_TINT_FRACTION = 0.35f

/**
 * Dashboard mood card. Stateless (plain data + lambdas), mirroring [WorkoutCard]. One clear action:
 * a single "Log your mood" button (and the card body) opens the log sheet — the old scattered
 * face-icon-scan and decorative "+" are gone. Below the summary, a "shape of my day" strip of
 * mood-dots previews today's logs; "View timeline ›" opens the full browsable history.
 */
@Composable
fun EmotionsCard(
    latest: EmotionRecord?,
    dominantToday: EmotionType?,
    todayCount: Int,
    today: List<EmotionRecord>,
    onLog: () -> Unit,
    onOpenTimeline: () -> Unit
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onLog),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = latest?.emotion?.emoji() ?: "🙂",
                    fontSize = 40.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mood",
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceColor.copy(alpha = 0.5f)
                    )
                    Text(
                        text = latest?.emotion?.displayLabel() ?: "Log how you feel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                    val subtitle = when {
                        latest == null -> "Tap to log your first mood"
                        todayCount > 1 && dominantToday != null ->
                            "Today mostly ${dominantToday.displayLabel().lowercase()} · $todayCount logged"
                        else -> "Logged today"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceColor.copy(alpha = 0.55f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalIconButton(onClick = onLog) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Log your mood",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // "Shape of my day" — today's logs, oldest→newest left→right (input is newest-first).
            AnimatedVisibility(visible = today.isNotEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(
                            items = today.reversed(),
                            key = { it.id }
                        ) { record ->
                            MoodDot(record.emotion)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onOpenTimeline,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) {
                Text("View timeline")
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** A 28.dp tonal circle tinted toward the emotion's accent (lerp from primary — never raw hex). */
@Composable
private fun MoodDot(emotion: EmotionType) {
    val primary = MaterialTheme.colorScheme.primary
    val tint = lerp(primary, moodAccentFor(emotion).accent, DOT_TINT_FRACTION)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emotion.emoji(), fontSize = 15.sp)
    }
}
