package com.hellohealth.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import com.hellohealth.domain.model.HealthSummary

@Composable
fun WorkoutCard(
    summary: HealthSummary,
    hasPermissions: Boolean,
    healthConnectAvailability: Int,
    isSyncing: Boolean,
    lastSyncTime: Long?,
    error: String?,
    onPermissionRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onSync: () -> Unit,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable(enabled = hasPermissions, onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                        if (lastSyncTime != null) {
                            Text(
                                text = "Updated ${formatLastSync(lastSyncTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceColor.copy(alpha = 0.6f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSync,
                        modifier = Modifier
                            .size(36.dp)
                            .background(primaryColor.copy(alpha = 0.1f), CircleShape)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = primaryColor)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!hasPermissions) {
                    PermissionRequestContent(
                        availability = healthConnectAvailability,
                        error = error,
                        onPermissionRequest = onPermissionRequest,
                        onOpenSettings = onOpenSettings
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Steps Progress Ring
                        Box(contentAlignment = Alignment.Center) {
                            val stepsProgress = (summary.steps.toFloat() / summary.stepsGoal).coerceIn(0.01f, 1f)
                            val animatedProgress by animateFloatAsState(
                                targetValue = if (isSyncing) 0.01f else stepsProgress,
                                animationSpec = tween(durationMillis = 1000),
                                label = "steps_progress"
                            )

                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(100.dp),
                                color = primaryColor,
                                strokeWidth = 10.dp,
                                trackColor = primaryColor.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = summary.steps.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = onSurfaceColor
                                )
                                Text(
                                    text = "steps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceColor.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Other metrics
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricSmall(
                                value = "${summary.activeCalories.toInt()} kcal",
                                label = "Active Burn",
                                color = Color(0xFFFF7043)
                            )
                            MetricSmall(
                                value = "${summary.activeTimeMinutes} min",
                                label = "Active Time",
                                color = Color(0xFF42A5F5)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSmall(value: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PermissionRequestContent(
    availability: Int,
    error: String?,
    onPermissionRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isAndroid14 = android.os.Build.VERSION.SDK_INT >= 34
    
    val message = when {
        availability == HealthConnectClient.SDK_AVAILABLE || isAndroid14 -> "Connect HelloHealth to your fitness data to see your activity rings."
        availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect needs an update to work properly."
        else -> "Health Connect setup is required to track your activity."
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onPermissionRequest,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Connect Now", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        TextButton(onClick = onOpenSettings) {
            Text("Open Health Settings", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatLastSync(time: Long): String {
    val diff = System.currentTimeMillis() - time
    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000} min ago"
        else -> "today"
    }
}
