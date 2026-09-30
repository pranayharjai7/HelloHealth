package com.hellohealth.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.ui.dashboard.NutritionUiState
import com.hellohealth.ui.theme.LocalMoodAccent

/** Fraction the calorie ring/accent is lerped toward the mood accent — never raw hex into a role. */
private const val ACCENT_TINT_FRACTION = 0.55f

/**
 * Dashboard Nutrition card. Stateless (plain [NutritionUiState] + [onClick]), mirroring
 * [VitalsCard]/[WorkoutCard]. Shows a calorie ring (consumed vs budget, reusing [ActivityRing]) with
 * the flagship energy-balance net beneath it, plus macro (P/C/F) progress bars and a water chip.
 * The accent is lerped from `primary` toward the current mood accent so the card re-tints with mood.
 *
 * In the zero state ([NutritionUiState.hasData] false — no entries, no burn, no budget) the card
 * shows a gentle "start logging" hint instead of an all-zero ring. Tapping opens the full screen.
 */
@Composable
fun NutritionCard(
    state: NutritionUiState,
    onClick: () -> Unit,
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val moodAccent = LocalMoodAccent.current
    val accent = lerp(primary, moodAccent.accent, ACCENT_TINT_FRACTION)

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Nutrition",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = onSurfaceColor,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!state.hasData) {
                Text(
                    text = "Log your first meal to see calories, macros and your energy balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceColor.copy(alpha = 0.6f),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CalorieRing(state = state, accent = accent)

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MacroBar("Protein", state.proteinG, state.proteinTargetG, Color(0xFF42A5F5))
                        MacroBar("Carbs", state.carbsG, state.carbsTargetG, Color(0xFFFFA726))
                        MacroBar("Fat", state.fatG, state.fatTargetG, Color(0xFFEF5350))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF26C6DA))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = state.waterLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = onSurfaceColor,
                                )
                                Text(
                                    text = "Water",
                                    fontSize = 10.sp,
                                    color = onSurfaceColor.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onClick,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text("Log food")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Calorie ring: consumed vs budget via [ActivityRing], with the net energy-balance line beneath. */
@Composable
private fun CalorieRing(state: NutritionUiState, accent: Color) {
    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        ActivityRing(
            progress = state.caloriesProgress.coerceIn(0.01f, 1f),
            color = accent,
            modifier = Modifier.fillMaxWidth(),
            strokeWidth = 10.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.caloriesConsumed.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (state.calorieBudget != null) "of ${state.calorieBudget}" else "kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * A macro progress bar: consumed grams vs target. When no target is available (sparse profile) the
 * bar is hidden and only the consumed grams show, so a missing budget never implies a full/empty bar.
 */
@Composable
private fun MacroBar(label: String, consumedG: Int, targetG: Int?, color: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 11.sp, color = onSurface.copy(alpha = 0.7f))
            Text(
                text = if (targetG != null) "${consumedG}g / ${targetG}g" else "${consumedG}g",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
            )
        }
        if (targetG != null && targetG > 0) {
            LinearProgressIndicator(
                progress = { (consumedG.toFloat() / targetG).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
            )
        }
    }
}
