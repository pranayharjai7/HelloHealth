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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.vitals.ReadinessStatus
import com.hellohealth.ui.dashboard.VitalsUiState

/** Fraction the readiness ring/status colour is lerped toward its band accent — never raw hex into a role. */
private const val STATUS_TINT_FRACTION = 0.55f

/**
 * Dashboard Vitals & Recovery card. Stateless (plain [VitalsUiState] + lambdas), mirroring
 * [WorkoutCard]/[EmotionsCard]. Shows a readiness ring (reusing [ActivityRing]'s drawArc idiom, scaled
 * 0-100) whose colour is tinted toward the band accent via [lerp] from `primary`, plus a strip of
 * latest-vitals chips. Tapping the card opens the trends screen.
 *
 * When Health Connect is not connected ([isConnected] false) the card shows the same connect prompt as
 * [WorkoutCard] instead of the ring — the gate keys on permission grant, supplied by the screen.
 */
@Composable
fun VitalsCard(
    state: VitalsUiState,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onClick: () -> Unit,
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable(enabled = isConnected, onClick = onClick),
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
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Vitals & Recovery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = onSurfaceColor,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isConnected) {
                VitalsConnectPrompt(onConnect = onConnect)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ReadinessRing(state = state)

                    Spacer(modifier = Modifier.width(20.dp))

                    // Latest-vitals chips. Each renders a dash when its value is null (missing sensor).
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            VitalChip("RHR", state.restingHeartRate, Color(0xFFEF5350), Modifier.weight(1f))
                            VitalChip("HRV", state.hrvRmssd, Color(0xFF42A5F5), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            VitalChip("SpO₂", state.spo2, Color(0xFF66BB6A), Modifier.weight(1f))
                            VitalChip("Resp", state.respiratoryRate, Color(0xFFAB47BC), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            VitalChip("Temp", state.bodyTemperature, Color(0xFFFFA726), Modifier.weight(1f))
                            VitalChip("Water", state.hydrationMl, Color(0xFF26C6DA), Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onClick,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text("View trends")
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

/**
 * Readiness ring: the [ActivityRing] drawArc idiom scaled to 0-100, coloured by band. While
 * establishing (< 7 days of rollup) the centre shows "n/7" instead of a score, so a thin baseline
 * never implies a real readiness number.
 */
@Composable
private fun ReadinessRing(state: VitalsUiState) {
    val statusColor = readinessColor(state.status)
    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        ActivityRing(
            progress = if (state.isEstablishingBaseline) {
                (state.establishingDayCount / 7f).coerceIn(0.01f, 1f)
            } else {
                (state.readinessScore / 100f).coerceIn(0.01f, 1f)
            },
            color = statusColor,
            modifier = Modifier.fillMaxWidth(),
            strokeWidth = 10.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isEstablishingBaseline) {
                Text(
                    text = "${state.establishingDayCount}/7",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "baseline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    text = state.readinessScore.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = readinessLabel(state.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A labelled vitals value; [value] is already the dash when the reading is missing. */
@Composable
private fun VitalChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/** Compact connect prompt mirroring [WorkoutCard]'s — shown when Health Connect isn't granted. */
@Composable
private fun VitalsConnectPrompt(onConnect: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Connect Health Connect to track resting heart rate, HRV and recovery.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onConnect,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Connect Now", fontWeight = FontWeight.Bold)
        }
    }
}

/** Band accent lerped from `primary` — never a raw hex into a Material3 role. */
@Composable
private fun readinessColor(status: ReadinessStatus): Color {
    val primary = MaterialTheme.colorScheme.primary
    val accent = when (status) {
        ReadinessStatus.OPTIMAL -> Color(0xFF2E7D32)
        ReadinessStatus.GOOD -> Color(0xFF66BB6A)
        ReadinessStatus.MODERATE -> Color(0xFFFFA726)
        ReadinessStatus.NEEDS_RECOVERY -> Color(0xFFEF5350)
        ReadinessStatus.INSUFFICIENT_DATA -> primary
    }
    return lerp(primary, accent, STATUS_TINT_FRACTION)
}

private fun readinessLabel(status: ReadinessStatus): String = when (status) {
    ReadinessStatus.OPTIMAL -> "Optimal"
    ReadinessStatus.GOOD -> "Good"
    ReadinessStatus.MODERATE -> "Moderate"
    ReadinessStatus.NEEDS_RECOVERY -> "Recover"
    ReadinessStatus.INSUFFICIENT_DATA -> "—"
}
