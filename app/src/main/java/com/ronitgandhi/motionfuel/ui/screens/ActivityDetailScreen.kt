package com.ronitgandhi.motionfuel.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.BuildConfig
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
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var shareMap by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var confirmFullRouteShare by remember { mutableStateOf(false) }
    val imperial = units == UnitSystem.IMPERIAL
    val pace = workout.averagePaceSecPerKm?.let { if (imperial) it * 1.609344 else it }
    val beginShare = {
        sharing = true
        shareError = null
        val createAndShare: (android.graphics.Bitmap?) -> Unit = { mapBitmap ->
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        ActivityShareImage.createShareIntent(context, workout, units, darkTheme, mapBitmap)
                    }
                }
                sharing = false
                result.onSuccess { context.startActivity(Intent.createChooser(it, "Share activity")) }
                    .onFailure { shareError = it.localizedMessage ?: "The activity image could not be created." }
            }
        }
        val currentMap = shareMap
        if (currentMap == null) {
            createAndShare(null)
        } else {
            runCatching {
                // GoogleMap snapshots must be requested from the UI thread after its tiles finish loading.
                currentMap.snapshot { bitmap ->
                    if (bitmap == null) {
                        sharing = false
                        shareError = "The map image is not ready yet. Please try again."
                    } else {
                        createAndShare(bitmap)
                    }
                }
            }.onFailure {
                sharing = false
                shareError = it.localizedMessage ?: "The map image could not be captured."
            }
        }
    }

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
            item {
                RouteMap(
                    route = workout.route,
                    modifier = Modifier.fillMaxWidth().aspectRatio(952f / 580f),
                    onMapReady = { shareMap = it },
                )
            }
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
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { confirmFullRouteShare = true },
                            enabled = !sharing && (!BuildConfig.MAPS_API_KEY_CONFIGURED || shareMap != null),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                        ) {
                            if (sharing) CircularProgressIndicator(modifier = Modifier.size(22.dp)) else {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Share activity image")
                            }
                        }
                        if (BuildConfig.MAPS_API_KEY_CONFIGURED && shareMap == null && !sharing) {
                            Text("Waiting for the map to finish loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        shareError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (confirmFullRouteShare) {
        AlertDialog(
            onDismissRequest = { confirmFullRouteShare = false },
            title = { Text("Share complete route?") },
            text = { Text("The image includes the complete route, including its start and finish locations. Only share it with people and apps you trust.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmFullRouteShare = false
                        beginShare()
                    },
                ) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { confirmFullRouteShare = false }) { Text("Cancel") } },
        )
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
