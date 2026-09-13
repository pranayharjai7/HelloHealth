package com.hellohealth.ui.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType

/**
 * Dashboard mood card. Stateless (plain data + onClick lambdas), mirroring [WorkoutCard]. Shows
 * the latest logged mood as a big emoji + label and, when more than one mood was logged today,
 * today's dominant mood. Tapping the card opens the manual log screen; the face icon opens the
 * on-device mood scanner ([onScan]).
 */
@Composable
fun EmotionsCard(
    latest: EmotionRecord?,
    dominantToday: EmotionType?,
    todayCount: Int,
    onClick: () -> Unit,
    onScan: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
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
                IconButton(onClick = onScan) {
                    Icon(
                        imageVector = Icons.Default.FaceRetouchingNatural,
                        contentDescription = "Scan your mood",
                        tint = primaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Log a mood",
                        tint = primaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
