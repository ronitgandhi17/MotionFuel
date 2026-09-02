package com.ronitgandhi.motionfuel.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.share.ActivityShareImage
import com.ronitgandhi.motionfuel.ui.components.RouteMap
import com.ronitgandhi.motionfuel.ui.components.formatDistance
import com.ronitgandhi.motionfuel.ui.components.formatDuration
import com.ronitgandhi.motionfuel.ui.components.formatPace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(workout: WorkoutSummary, units: UnitSystem, darkTheme: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trimEndpoints by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val imperial = units == UnitSystem.IMPERIAL
    val pace = workout.averagePaceSecPerKm?.let { if (imperial) it * 1.609344 else it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity summary", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(workout.type.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(SimpleDateFormat("EEEE, d MMMM yyyy • h:mm a", Locale.getDefault()).format(Date(workout.startedAtMillis)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { RouteMap(workout.route, Modifier.fillMaxWidth().height(300.dp)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivityMetric("Distance", formatDistance(workout.distanceMeters, imperial), Modifier.weight(1f))
                    ActivityMetric("Moving time", formatDuration(workout.durationSeconds), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivityMetric("Average pace", "${formatPace(pace)} /${if (imperial) "mi" else "km"}", Modifier.weight(1f))
                    ActivityMetric("Energy", "${workout.caloriesKcal.toInt()} kcal", Modifier.weight(1f))
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Activity details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        DetailRow("Steps", workout.steps.toString())
                        DetailRow("Elevation gain", "${workout.elevationGainMeters.toInt()} m")
                        DetailRow("Dominant movement", workout.dominantActivity.name.lowercase().replaceFirstChar(Char::uppercase))
                        DetailRow("GPS samples removed", workout.rejectedGpsPoints.toString())
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Share activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Creates a 1080 × 1350 image with your route outline and activity statistics.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Trim route endpoints", fontWeight = FontWeight.SemiBold)
                                Text("Reduces exposure of your start and finish locations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = trimEndpoints, onCheckedChange = { trimEndpoints = it })
                        }
                        Button(
                            onClick = {
                                sharing = true
                                shareError = null
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) { ActivityShareImage.createShareIntent(context, workout, units, darkTheme, trimEndpoints) }
                                    }
                                    sharing = false
                                    result.onSuccess { context.startActivity(Intent.createChooser(it, "Share activity")) }
                                        .onFailure { shareError = it.localizedMessage ?: "The activity image could not be created." }
                                }
                            },
                            enabled = !sharing,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                        ) {
                            if (sharing) CircularProgressIndicator(modifier = Modifier.size(22.dp)) else {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Share activity image")
                            }
                        }
                        shareError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
