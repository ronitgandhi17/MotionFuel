package com.ronitgandhi.motionfuel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.LocationQuality
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutTelemetry
import com.ronitgandhi.motionfuel.ui.components.InsightCard
import com.ronitgandhi.motionfuel.ui.components.MetricCard
import com.ronitgandhi.motionfuel.ui.components.RouteCanvas
import com.ronitgandhi.motionfuel.ui.components.formatDistance
import com.ronitgandhi.motionfuel.ui.components.formatDuration
import com.ronitgandhi.motionfuel.ui.components.formatPace
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelRose
import com.ronitgandhi.motionfuel.ui.theme.FuelSky

@Composable
fun WorkoutScreen(
    telemetry: WorkoutTelemetry,
    insights: List<Insight>,
    units: UnitSystem,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
    onDone: () -> Unit,
) {
    val complete = telemetry.status == WorkoutStatus.COMPLETE
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (complete) "Workout saved" else telemetry.type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        if (complete) "Saved to your activity history" else "Real sensors • foreground tracking",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = when (telemetry.gpsQuality) {
                        LocationQuality.GOOD -> FuelGreen.copy(alpha = 0.16f)
                        LocationQuality.FAIR -> FuelOrange.copy(alpha = 0.16f)
                        LocationQuality.POOR -> FuelRose.copy(alpha = 0.16f)
                    },
                ) {
                    Text("GPS ${telemetry.gpsQuality.name.lowercase()}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            RouteCanvas(telemetry.route, Modifier.fillMaxWidth().height(245.dp), telemetry.rejectedGpsPoints > 0)
        }
        item {
            Text(formatDuration(telemetry.elapsedSeconds), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text("elapsed time", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Distance", formatDistance(telemetry.distanceMeters, units == UnitSystem.IMPERIAL), "Filtered route", FuelGreen, Modifier.weight(1f))
                MetricCard("Current pace", formatPace(telemetry.currentPaceSecPerKm), "min / km", FuelSky, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Cadence", "${telemetry.cadenceSpm}", "steps / min", FuelOrange, Modifier.weight(1f))
                MetricCard("Elevation", "${telemetry.elevationGainMeters.toInt()} m", "positive gain", FuelRose, Modifier.weight(1f))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(telemetry.activity.type.name.lowercase()) })
                    AssistChip(onClick = {}, label = { Text("${(telemetry.activity.confidence * 100).toInt()}% confidence") })
                }
                if (telemetry.rejectedGpsPoints > 0) {
                    AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Rounded.LocationOff, null) }, label = { Text("${telemetry.rejectedGpsPoints} GPS samples rejected") })
                }
            }
        }
        insights.firstOrNull()?.let { insight -> item { InsightCard(insight) } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryMetric("Average pace", formatPace(telemetry.averagePaceSecPerKm))
                    SummaryMetric("Steps", telemetry.steps.toString())
                    SummaryMetric("Energy", "${telemetry.caloriesKcal.toInt()} kcal")
                }
            }
        }
        item {
            if (complete) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("View activity history")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onPauseResume, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(if (telemetry.status == WorkoutStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (telemetry.status == WorkoutStatus.PAUSED) "Resume" else "Pause")
                    }
                    OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Rounded.Flag, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Finish")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
