package com.hellohealth.ui.activitydetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.hellohealth.BuildConfig
import com.hellohealth.domain.model.ActivityChartPoint
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityRoutePoint
import com.hellohealth.domain.model.ActivityTimelineEntry
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    viewModel: ActivityDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Activity Details", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.16f),
                            backgroundColor
                        )
                    )
                )
                .padding(padding)
        ) {
            when (val currentState = state) {
                ActivityDetailState.Loading -> LoadingState()
                is ActivityDetailState.Error -> ErrorState(
                    message = currentState.message,
                    onRetry = viewModel::retry
                )
                is ActivityDetailState.Success -> {
                    val context = LocalContext.current
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val isGranted = permissions.values.any { it }
                        if (isGranted) {
                            viewModel.retry() // Reload to refresh map state if needed
                        }
                    }

                    LaunchedEffect(Unit) {
                        val hasFineLocation = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasCoarseLocation = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasFineLocation && !hasCoarseLocation) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                    ActivityDetailContent(detail = currentState.detail)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading workout analytics...",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        FrostedCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Please try again. If the workout is old, route or chart data may no longer be available from the source app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun ActivityDetailContent(detail: ActivityDetail) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { ActivityHeader(detail) }
        item { RouteSection(detail) }
        item { KeyMetricsSection(detail) }
        item {
            AnalyticsChartCard(
                title = "Heart Rate",
                subtitle = detail.averageHeartRate?.let { "Average $it bpm" } ?: "No average available",
                points = detail.heartRatePoints,
                lineColor = Color(0xFFE75A7C),
                fillColor = Color(0xFFE75A7C).copy(alpha = 0.18f),
                markerValue = detail.averageHeartRate?.toFloat(),
                emptyMessage = "No heart rate samples were recorded during this workout.",
                valueFormatter = { "${it.roundToInt()} bpm" },
                durationSeconds = detail.durationSeconds
            )
        }
        item {
            AnalyticsChartCard(
                title = "Pace",
                subtitle = detail.averagePaceSecondsPerKm?.let { "Average ${formatPace(it)}" } ?: "No pace average available",
                points = detail.pacePoints,
                lineColor = Color(0xFF3B82F6),
                fillColor = Color(0xFF3B82F6).copy(alpha = 0.18f),
                emptyMessage = "No pace data available for this workout.",
                valueFormatter = { formatPace(it.toDouble()) },
                durationSeconds = detail.durationSeconds
            )
        }
        item {
            AnalyticsChartCard(
                title = "Elevation",
                subtitle = buildString {
                    append("Gain ")
                    append(detail.elevationGainMeters?.let(::formatMeters) ?: "--")
                    append("  •  Loss ")
                    append(detail.elevationLossMeters?.let(::formatMeters) ?: "--")
                },
                points = detail.elevationPoints,
                lineColor = Color(0xFF16A34A),
                fillColor = Color(0xFF16A34A).copy(alpha = 0.18f),
                emptyMessage = "No elevation profile is available for this workout.",
                valueFormatter = { formatMeters(it.toDouble()) },
                durationSeconds = detail.durationSeconds
            )
        }
        item { DetailedStatsSection(detail) }
        item { TimelineSection(detail.timeline) }
        item { DataSourceSection(detail) }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ActivityHeader(detail: ActivityDetail) {
    val activityColor = when (detail.activityName) {
        "Running" -> Color(0xFFEF4444)
        "Walking" -> Color(0xFF10B981)
        "Cycling" -> Color(0xFF3B82F6)
        else -> MaterialTheme.colorScheme.primary
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            activityColor.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(activityColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = activityIcon(detail.activityName),
                            contentDescription = null,
                            tint = activityColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = formatSessionDate(detail.startTime),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${formatClock(detail.startTime)} - ${formatClock(detail.endTime)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Duration: ${formatDuration(detail.durationSeconds)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailPill(
                        icon = Icons.Default.LocalFireDepartment,
                        label = detail.caloriesBurned?.let(::formatCaloriesValue) ?: "--"
                    )
                    DetailPill(
                        icon = Icons.Default.Route,
                        label = detail.distanceKm?.let(::formatDistanceKm) ?: "--"
                    )
                    detail.steps?.let {
                        DetailPill(
                            icon = Icons.Default.DirectionsWalk,
                            label = "$it steps"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RouteSection(detail: ActivityDetail) {
    FrostedCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Workout Route", "Follow the path captured during the session.")
            when {
                BuildConfig.GOOGLE_MAPS_API_KEY.isBlank() -> EmptyAnalyticsState("Add GOOGLE_MAPS_API_KEY to local.properties to enable the route map.")
                else -> ActivityRouteMap(routePoints = detail.routePoints)
            }
        }
    }
}

@Composable
private fun ActivityRouteMap(routePoints: List<ActivityRoutePoint>) {
    val context = LocalContext.current
    val hasLocationPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPositionState = rememberCameraPositionState()
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = hasLocationPermission,
            mapToolbarEnabled = false,
            compassEnabled = true
        )
    }
    val mapProperties = remember(hasLocationPermission) {
        MapProperties(
            mapStyleOptions = MapStyleOptions(DarkMapStyleJson),
            isMyLocationEnabled = hasLocationPermission
        )
    }
    val polylinePoints = remember(routePoints) {
        routePoints.map { LatLng(it.latitude, it.longitude) }
    }
    val start = polylinePoints.firstOrNull()
    val end = polylinePoints.lastOrNull()

    LaunchedEffect(polylinePoints) {
        if (polylinePoints.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.builder()
            polylinePoints.forEach(boundsBuilder::include)
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
        } else if (hasLocationPermission) {
            // If no route, try to center on current location
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        cameraPositionState.move(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Should not happen as we checked permission
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            if (polylinePoints.size >= 2) {
                Polyline(
                    points = polylinePoints,
                    color = Color(0xFF22C55E),
                    width = 9f
                )
            }
            start?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Start",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }
            end?.takeIf { it != start }?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Finish",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }
        }
    }
}

@Composable
private fun KeyMetricsSection(detail: ActivityDetail) {
    val metrics = buildList {
        add(MetricCellData("Distance", detail.distanceKm?.let(::formatDistanceKm) ?: "--", Icons.Default.Route, Color(0xFF2563EB)))
        add(MetricCellData("Calories", detail.caloriesBurned?.let(::formatCaloriesValue) ?: "--", Icons.Default.LocalFireDepartment, Color(0xFFEA580C)))
        add(MetricCellData("Active Time", "${detail.activeMinutes} min", Icons.Default.AccessTime, Color(0xFF9333EA)))
        add(MetricCellData("Move Minutes", "${detail.moveMinutes} min", Icons.Default.DirectionsRun, Color(0xFF16A34A)))
        add(MetricCellData("Avg Heart Rate", detail.averageHeartRate?.let { "$it bpm" } ?: "--", Icons.Default.Favorite, Color(0xFFE11D48)))
        add(MetricCellData("Average Pace", detail.averagePaceSecondsPerKm?.let(::formatPace) ?: "--", Icons.Default.Speed, Color(0xFF0284C7)))
    }

    FrostedCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Key Metrics", "The headline numbers from this workout.")
            MetricGrid(metrics)
        }
    }
}

@Composable
private fun DetailedStatsSection(detail: ActivityDetail) {
    val stats = buildList {
        add(StatRowData("Average pace", detail.averagePaceSecondsPerKm?.let(::formatPace) ?: "--"))
        add(StatRowData("Fastest pace", detail.fastestPaceSecondsPerKm?.let(::formatPace) ?: "--"))
        add(StatRowData("Elevation gain", detail.elevationGainMeters?.let(::formatMeters) ?: "--"))
        add(StatRowData("Elevation loss", detail.elevationLossMeters?.let(::formatMeters) ?: "--"))
        add(StatRowData("Average heart rate", detail.averageHeartRate?.let { "$it bpm" } ?: "--"))
        add(StatRowData("Max heart rate", detail.maxHeartRate?.let { "$it bpm" } ?: "--"))
        add(StatRowData("Calories burned", detail.caloriesBurned?.let(::formatCaloriesValue) ?: "--"))
        add(StatRowData("Distance", detail.distanceKm?.let(::formatDistanceKm) ?: "--"))
        add(StatRowData("Steps", detail.steps?.toString() ?: "--"))
        add(StatRowData("Active minutes", "${detail.activeMinutes} min"))
    }

    FrostedCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Detailed Stats", "A fuller breakdown for trend checking and post-workout review.")
            stats.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    row.forEach { stat ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stat.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stat.value,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineSection(timeline: List<ActivityTimelineEntry>) {
    FrostedCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Workout Timeline", "Session milestones and structured blocks captured by Health Connect.")
            if (timeline.isEmpty()) {
                EmptyAnalyticsState("No workout timeline was recorded for this session.")
            } else {
                timeline.sortedBy { it.timestamp }.forEachIndexed { index, entry ->
                    TimelineRow(
                        entry = entry,
                        isLast = index == timeline.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    entry: ActivityTimelineEntry,
    isLast: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 6.dp)
        ) {
            Text(
                text = formatClock(entry.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            entry.subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                )
            }
            entry.value?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun DataSourceSection(detail: ActivityDetail) {
    FrostedCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Data Source", "Where this activity detail was synced from.")
            AssistChip(
                onClick = {},
                label = { Text("Data Source: ${detail.dataSourceLabel}") },
                leadingIcon = {
                    Icon(Icons.Default.Favorite, contentDescription = null)
                }
            )
            Text(
                text = "Synced from: ${detail.syncedFromLabel ?: "Unknown source app"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun AnalyticsChartCard(
    title: String,
    subtitle: String,
    points: List<ActivityChartPoint>,
    lineColor: Color,
    fillColor: Color,
    emptyMessage: String,
    durationSeconds: Long,
    valueFormatter: (Float) -> String,
    markerValue: Float? = null
) {
    FrostedCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(title, subtitle)
            if (points.isEmpty()) {
                EmptyAnalyticsState(emptyMessage)
            } else {
                val chartProgress: Float by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 850),
                    label = "${title}_chart"
                )
                val minValue = remember(points) { points.minOfOrNull { it.value } ?: 0f }
                val maxValue = remember(points) { points.maxOfOrNull { it.value } ?: 0f }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = valueFormatter(maxValue),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Text(
                        text = valueFormatter(minValue),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f)
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .graphicsLayer(
                            alpha = chartProgress,
                            translationY = (1f - chartProgress) * 36f
                        )
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    val safeMaxX: Float = (points.maxOfOrNull { it.minutesFromStart } ?: 1f).coerceAtLeast(1f)
                    val rawMinY: Float = points.minOfOrNull { it.value } ?: 0f
                    val rawMaxY: Float = points.maxOfOrNull { it.value } ?: 0f
                    val yPadding = if (abs(rawMaxY - rawMinY) < 0.01f) 1f else (rawMaxY - rawMinY) * 0.12f
                    val minY = rawMinY - yPadding
                    val maxY = rawMaxY + yPadding

                    fun mapX(minutes: Float): Float = (minutes / safeMaxX) * chartWidth
                    fun mapY(value: Float): Float {
                        val ratio = (value - minY) / (maxY - minY)
                        return chartHeight - (ratio * chartHeight)
                    }

                    val linePath = Path()
                    val fillPath = Path()
                    points.forEachIndexed { index, point ->
                        val x = mapX(point.minutesFromStart)
                        val y = mapY(point.value)
                        if (index == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, chartHeight)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(chartWidth, chartHeight)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                fillColor.copy(alpha = 0.42f),
                                fillColor.copy(alpha = 0.03f)
                            )
                        )
                    )

                    markerValue?.let { marker ->
                        val markerY = mapY(marker)
                        drawLine(
                            color = lineColor.copy(alpha = 0.4f),
                            start = androidx.compose.ui.geometry.Offset(0f, markerY),
                            end = androidx.compose.ui.geometry.Offset(chartWidth, markerY),
                            strokeWidth = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                        )
                    }

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )

                    points.maxByOrNull { it.value }?.let { peak ->
                        drawCircle(
                            color = lineColor,
                            radius = 8f,
                            center = androidx.compose.ui.geometry.Offset(
                                mapX(peak.minutesFromStart),
                                mapY(peak.value)
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0m",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Text(
                        text = formatDuration(durationSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FrostedCard(
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                        )
                    )
                )
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
    }
}

@Composable
private fun EmptyAnalyticsState(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
            )
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<MetricCellData>) {
    metrics.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            row.forEach { metric ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, metric.color.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(metric.color.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = metric.icon,
                                contentDescription = null,
                                tint = metric.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = metric.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = metric.value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class MetricCellData(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

private data class StatRowData(
    val label: String,
    val value: String
)

private fun activityIcon(activityName: String) = when (activityName) {
    "Running" -> Icons.Default.DirectionsRun
    "Walking" -> Icons.Default.DirectionsWalk
    "Cycling" -> Icons.AutoMirrored.Filled.DirectionsBike
    else -> Icons.Default.FitnessCenter
}

private fun formatSessionDate(time: Instant): String =
    DateTimeFormatter.ofPattern("MMMM d").withZone(ZoneId.systemDefault()).format(time)

private fun formatClock(time: Instant): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(time)

private fun formatDuration(seconds: Long): String {
    val duration = Duration.ofSeconds(seconds)
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    val remainingSeconds = duration.seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${remainingSeconds}s"
        else -> "${remainingSeconds}s"
    }
}

private fun formatDistanceKm(distanceKm: Double): String = String.format("%.2f km", distanceKm)

private fun formatCaloriesValue(calories: Double): String = "${calories.roundToInt()} kcal"

private fun formatMeters(meters: Double): String =
    if (meters >= 1000) String.format("%.2f km", meters / 1000.0) else "${meters.roundToInt()} m"

private fun formatPace(secondsPerKm: Double): String {
    val totalSeconds = secondsPerKm.roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d / km", minutes, seconds)
}

private const val DarkMapStyleJson = """
[
  {"elementType":"geometry","stylers":[{"color":"#162227"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#d0d8db"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#162227"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#24363d"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#1c2c31"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0b4b5a"}]}
]
"""
