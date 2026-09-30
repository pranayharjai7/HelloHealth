package com.hellohealth.ui.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.ui.dashboard.CoachingViewModel

/**
 * Dashboard AI Coaching card. Stateless (plain [CoachingViewModel.CoachingUiState] + [onClick]),
 * mirroring the other cards. Shows the daily insight; a subtle "offline coach" note appears when the
 * text came from the on-device rule-based fallback (LLM unavailable or coaching not opted in), so the
 * user always understands the source without it ever reading as an error. Mood-tinted for free via
 * `colorScheme.primary`. Tapping opens the full coaching screen (insight + chat).
 */
@Composable
fun CoachingCard(
    state: CoachingViewModel.CoachingUiState,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "AI Coach",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Reading your day…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                state.hasInsight -> {
                    Text(
                        text = state.insight,
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurface,
                    )
                    if (state.isRuleBased) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Offline coach · enable AI Coaching for personalised insights",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Tap to get an insight across your activity, nutrition, recovery and mood.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
