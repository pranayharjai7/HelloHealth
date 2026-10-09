package com.hellohealth.ui.charts

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The hybrid trends layout: a full-width HERO chart (the first/primary metric) on top, then a 2-column
 * grid of compact sparkline TILES for the rest. Tapping a tile expands it in place into a full chart
 * (and collapses the previously-expanded one). Each card shows its latest value + relative
 * "last recorded" label. Metrics with fewer than 2 points show an empty hint rather than a chart.
 */
@Composable
fun TrendHybridLayout(metrics: List<TrendMetric>, modifier: Modifier = Modifier) {
    if (metrics.isEmpty()) return
    val hero = metrics.first()
    val tiles = metrics.drop(1)
    // Index into [tiles] of the one expanded into a full chart; -1 = all collapsed.
    var expanded by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroChartCard(hero)

        // 2-column grid of tiles. A tile expanded shows the full chart; others stay sparklines.
        tiles.chunked(2).forEachIndexed { rowIndex, rowMetrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                rowMetrics.forEachIndexed { colIndex, metric ->
                    val tileIndex = rowIndex * 2 + colIndex
                    MetricTile(
                        metric = metric,
                        expanded = expanded == tileIndex,
                        onToggle = { expanded = if (expanded == tileIndex) -1 else tileIndex },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowMetrics.size == 1) Spacer(Modifier.weight(1f)) // keep the last odd tile half-width
            }
        }
    }
}

@Composable
private fun HeroChartCard(metric: TrendMetric) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricHeader(metric, large = true)
            if (metric.points.size < 2) {
                EmptyHint(metric)
            } else {
                TrendLine(points = metric.points, lineColor = metric.color)
            }
        }
    }
}

@Composable
private fun MetricTile(metric: TrendMetric, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = tween(300))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricHeader(metric, large = false)
            if (metric.points.size < 2) {
                EmptyHint(metric)
            } else if (expanded) {
                TrendLine(points = metric.points, lineColor = metric.color)
            } else {
                Sparkline(points = metric.points, lineColor = metric.color)
            }
        }
    }
}

@Composable
private fun MetricHeader(metric: TrendMetric, large: Boolean) {
    Column {
        Text(
            text = metric.title,
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = metric.latestFormatted ?: "—",
                style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = metric.color,
            )
            metric.lastRecorded?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(metric: TrendMetric) {
    Text(
        text = if (metric.points.isEmpty()) "No readings yet" else "Only one reading so far",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.height(44.dp),
    )
}
