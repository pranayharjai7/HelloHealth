package com.hellohealth.ui.dashboard.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CardDefaults
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
import com.hellohealth.ui.dashboard.VitalsUiState
import java.time.LocalDate

/**
 * Merged dashboard card — a compact Activity snapshot (3 rings + legend via the shared [ActivityCard])
 * plus a readiness line, opening the unified Health screen. Stateless: fed the already-collected
 * steps/goals/activeCalories/activeMinutes plus the dashboard [VitalsUiState]. Values dash when
 * absent, never a fake 0.
 */
@Composable
fun HealthCard(
    selectedDate: LocalDate,
    steps: Long,
    goalSteps: Long,
    activeCalories: Double,
    goalActiveCalories: Int,
    activeMinutes: Long,
    goalActiveMinutes: Int,
    vitals: VitalsUiState,
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val isToday = selectedDate == LocalDate.now()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isToday) "Health" else "Health · past day",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = onSurface,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.height(20.dp))

            ActivityCard(
                steps = steps,
                goalSteps = goalSteps,
                activeCalories = activeCalories,
                goalActiveCalories = goalActiveCalories,
                activeMinutes = activeMinutes,
                goalActiveMinutes = goalActiveMinutes,
                ringSize = 104.dp,
            )

            Spacer(Modifier.height(16.dp))
            Chip("Readiness", if (vitals.hasReadiness && vitals.readinessScore > 0) "${vitals.readinessScore}/100" else "—")
        }
    }
}

@Composable
private fun Chip(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
