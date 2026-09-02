package com.ronitgandhi.motionfuel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
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
import com.ronitgandhi.motionfuel.BuildConfig
import com.ronitgandhi.motionfuel.R
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.InsightPriority
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelRose
import com.ronitgandhi.motionfuel.ui.theme.FuelSky
import java.util.Locale
import kotlin.math.max

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .background(FuelGreen, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("M", color = Color(0xFF062217), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onAction).padding(6.dp))
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, detail: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(9.dp).background(accent, CircleShape))
            Spacer(Modifier.height(14.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MacroProgress(label: String, value: Double, target: Double, color: Color, suffix: String = "g") {
    val progress = if (target <= 0) 0f else (value / target).toFloat().coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("${value.toInt()} / ${target.toInt()} $suffix", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
fun InsightCard(insight: Insight, modifier: Modifier = Modifier) {
    var expanded by remember(insight.id) { mutableStateOf(false) }
    val accent = when (insight.priority) {
        InsightPriority.HIGH -> FuelRose
        InsightPriority.MEDIUM -> FuelOrange
        InsightPriority.LOW -> FuelGreen
    }
    Card(
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = 0.18f), shape = CircleShape) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.padding(9.dp).size(20.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(insight.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${(insight.confidence * 100).toInt()}% confidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = if (expanded) "Hide evidence" else "Show evidence")
            }
            Text(insight.message, style = MaterialTheme.typography.bodyMedium)
            Text(if (expanded) "Hide evidence" else "Why am I seeing this?", color = accent, style = MaterialTheme.typography.labelLarge)
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    insight.evidence.forEach { item ->
                        AssistChip(
                            onClick = {},
                            label = { Text("${item.label}: ${item.value}") },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    }
                    Text("Wellness estimate only — not medical advice.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun EmptyInsightCard() {
    Card(colors = CardDefaults.cardColors(containerColor = FuelGreen.copy(alpha = 0.10f)), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = FuelGreen)
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Ready when you are", fontWeight = FontWeight.Bold)
                Text("Log food or complete an activity to generate context-aware guidance.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun RouteCanvas(route: List<GeoPoint>, modifier: Modifier = Modifier, showRejectedBadge: Boolean = false) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val routeColor = FuelGreen
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.background(surface, RoundedCornerShape(26.dp))) {
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val stepX = size.width / 5
            val stepY = size.height / 4
            for (i in 1..4) drawLine(grid, Offset(i * stepX, 0f), Offset(i * stepX, size.height), 1f)
            for (i in 1..3) drawLine(grid, Offset(0f, i * stepY), Offset(size.width, i * stepY), 1f)
            if (route.size > 1) {
                val minLat = route.minOf { it.latitude }
                val maxLat = route.maxOf { it.latitude }
                val minLon = route.minOf { it.longitude }
                val maxLon = route.maxOf { it.longitude }
                val latRange = max(maxLat - minLat, 0.00001)
                val lonRange = max(maxLon - minLon, 0.00001)
                val path = Path()
                route.forEachIndexed { index, point ->
                    val x = ((point.longitude - minLon) / lonRange * size.width).toFloat()
                    val y = (size.height - (point.latitude - minLat) / latRange * size.height).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, routeColor, style = Stroke(width = 8f, cap = StrokeCap.Round))
                val start = route.first()
                val end = route.last()
                fun position(point: GeoPoint) = Offset(
                    ((point.longitude - minLon) / lonRange * size.width).toFloat(),
                    (size.height - (point.latitude - minLat) / latRange * size.height).toFloat(),
                )
                drawCircle(FuelSky, 11f, position(start))
                drawCircle(FuelOrange, 11f, position(end))
            }
        }
        if (route.size < 2) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(10.dp))
                Text("Waiting for route", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showRejectedBadge) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = FuelOrange.copy(alpha = 0.18f),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOff, contentDescription = null, tint = FuelOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("GPS noise removed", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RouteMap(route: List<GeoPoint>, modifier: Modifier = Modifier, showRejectedBadge: Boolean = false) {
    // Uses the deterministic canvas only when the developer has not configured a Maps API key.
    if (!BuildConfig.MAPS_API_KEY_CONFIGURED) {
        RouteCanvas(route, modifier, showRejectedBadge)
        return
    }
    val coordinates = remember(route) { route.map { LatLng(it.latitude, it.longitude) } }
    val initialPosition = coordinates.lastOrNull() ?: LatLng(-37.8136, 144.9631)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition, if (coordinates.isEmpty()) 12f else 16f)
    }
    val context = LocalContext.current
    val useDarkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mapStyle = remember(useDarkMap) {
        if (useDarkMap) MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) else null
    }
    var mapLoaded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(mapLoaded, coordinates) {
        if (!mapLoaded) return@LaunchedEffect
        if (coordinates.size == 1) {
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(coordinates.first(), 16f), 450)
        } else {
            val bounds = LatLngBounds.builder().apply { coordinates.forEach(::include) }.build()
            cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 72), 450)
        }
    }
    Box(modifier.clip(RoundedCornerShape(26.dp))) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapStyleOptions = mapStyle),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true),
            onMapLoaded = { mapLoaded = true },
        ) {
            if (coordinates.size > 1) Polyline(points = coordinates, color = FuelGreen, width = 12f)
            coordinates.firstOrNull()?.let { Marker(state = MarkerState(it), title = "Start") }
            if (coordinates.size > 1) Marker(state = MarkerState(coordinates.last()), title = "Current position")
        }
        if (coordinates.isEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            ) {
                Text("Waiting for GPS fix", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showRejectedBadge) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = FuelOrange.copy(alpha = 0.88f),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("GPS noise removed", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)

fun formatPace(secondsPerKm: Double?): String {
    if (secondsPerKm == null || !secondsPerKm.isFinite() || secondsPerKm > 3_599) return "—"
    return String.format(Locale.US, "%d:%02d", (secondsPerKm / 60).toInt(), secondsPerKm.toInt() % 60)
}

fun formatDistance(meters: Double, imperial: Boolean): String = if (imperial) {
    String.format(Locale.US, "%.2f mi", meters / 1_609.344)
} else {
    String.format(Locale.US, "%.2f km", meters / 1_000.0)
}
