package com.ronitgandhi.motionfuel.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.model.AdaptiveFuelTarget
import com.ronitgandhi.motionfuel.domain.model.ConnectedHealthSnapshot
import com.ronitgandhi.motionfuel.domain.model.GoalProgress
import com.ronitgandhi.motionfuel.domain.model.PersonalRecords
import com.ronitgandhi.motionfuel.domain.model.RecoveryScore
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WeeklyReport
import com.ronitgandhi.motionfuel.domain.model.WellnessGoals
import com.ronitgandhi.motionfuel.ui.components.formatDistance
import com.ronitgandhi.motionfuel.ui.components.formatDuration
import com.ronitgandhi.motionfuel.ui.components.formatPace
import com.ronitgandhi.motionfuel.ui.theme.FuelBlue
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen

@Composable
fun AdaptiveFuelCard(target: AdaptiveFuelTarget, waterTodayMl: Int, onAddWater: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Adaptive fuel & hydration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${target.recommendedKcal} kcal today", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = FuelBlue)
            Text("${if (target.adjustmentKcal >= 0) "+" else ""}${target.adjustmentKcal} kcal from ${target.reasons.joinToString()}", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { (waterTodayMl.toFloat() / target.hydrationTargetMl).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalDrink, null, tint = FuelGreen)
                Spacer(Modifier.size(8.dp))
                Text("$waterTodayMl / ${target.hydrationTargetMl} ml", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { onAddWater(250) }) { Text("+250 ml") }
            }
            Text("Recommendations are bounded wellness estimates, not medical advice.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PersonalRecordsCard(records: PersonalRecords, imperial: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Personal records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AdvancedRow("Longest distance", formatDistance(records.longestDistanceMeters, imperial))
            AdvancedRow("Longest activity", formatDuration(records.longestDurationSeconds))
            AdvancedRow("Fastest pace", records.fastestPaceSecPerKm?.let { "${formatPace(if (imperial) it * 1.609344 else it)} /${if (imperial) "mi" else "km"}" } ?: "—")
            AdvancedRow("Most steps", records.mostSteps.toString())
            AdvancedRow("Current streak", "${records.currentWorkoutStreakDays} days")
        }
    }
}

@Composable
fun ProgressWellnessCards(
    recovery: RecoveryScore,
    goals: GoalProgress,
    report: WeeklyReport,
    settings: UserSettings,
    onUpdateGoals: (WellnessGoals) -> Unit,
    onUpdateRecovery: (Double, Int) -> Unit,
) {
    var editingGoals by remember { mutableStateOf(false) }
    var workoutTarget by remember(settings.wellnessGoals) { mutableStateOf(settings.wellnessGoals.weeklyWorkoutTarget.toString()) }
    var stepTarget by remember(settings.wellnessGoals) { mutableStateOf(settings.wellnessGoals.dailyStepTarget.toString()) }
    var waterTarget by remember(settings.wellnessGoals) { mutableStateOf(settings.wellnessGoals.dailyWaterTargetMl.toString()) }
    var sleep by remember(settings.manualSleepHours) { mutableStateOf(settings.manualSleepHours.toString()) }
    var heartRate by remember(settings.manualRestingHeartRateBpm) { mutableStateOf(settings.manualRestingHeartRateBpm.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recovery readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${recovery.score}/100 • ${recovery.label}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = FuelGreen)
            Text(recovery.recommendation)
            Text(recovery.factors.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sleep, { sleep = it }, label = { Text("Sleep h") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(heartRate, { heartRate = it }, label = { Text("Resting HR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = { onUpdateRecovery(sleep.toDoubleOrNull() ?: 7.5, heartRate.toIntOrNull() ?: 68) }, modifier = Modifier.fillMaxWidth()) { Text("Update recovery inputs") }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Goals & streaks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AdvancedProgress("Active days", goals.workoutDays, goals.workoutTarget)
            AdvancedProgress("Steps", goals.stepsToday.toInt(), goals.stepTarget)
            AdvancedProgress("Water (ml)", goals.waterTodayMl, goals.waterTargetMl)
            Text("${goals.workoutStreakDays}-day workout streak", fontWeight = FontWeight.SemiBold)
            val achievements = buildList {
                if (report.workouts > 0) add("First workout")
                if (goals.workoutStreakDays >= 3) add("3-day streak")
                if (goals.workoutStreakDays >= 7) add("7-day streak")
                if (goals.stepsToday >= goals.stepTarget) add("Step goal")
                if (goals.waterTodayMl >= goals.waterTargetMl) add("Hydration goal")
                if (report.consistencyScore >= 80) add("Consistency 80+")
            }
            Text(
                if (achievements.isEmpty()) "Complete a goal to unlock your first achievement." else "Achievements: ${achievements.joinToString(" • ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { editingGoals = !editingGoals }, modifier = Modifier.fillMaxWidth()) { Text(if (editingGoals) "Close goal editor" else "Edit goals") }
            if (editingGoals) {
                OutlinedTextField(workoutTarget, { workoutTarget = it }, label = { Text("Weekly active days") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stepTarget, { stepTarget = it }, label = { Text("Daily steps") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(waterTarget, { waterTarget = it }, label = { Text("Daily water (ml)") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onUpdateGoals(WellnessGoals(workoutTarget.toIntOrNull() ?: 3, stepTarget.toIntOrNull() ?: 10_000, waterTarget.toIntOrNull() ?: 2_500)); editingGoals = false }, modifier = Modifier.fillMaxWidth()) { Text("Save goals") }
            }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weekly insight report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${report.consistencyScore}% consistency", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            AdvancedRow("Workouts", report.workouts.toString())
            AdvancedRow("Active time", "${report.activeMinutes} min")
            AdvancedRow("Distance", formatDistance(report.distanceMeters, false))
            AdvancedRow("Avg food", "${report.averageFoodCaloriesKcal.toInt()} kcal/day")
            AdvancedRow("Avg protein", "${report.proteinAverageG.toInt()} g/day")
            report.weightChangeKg?.let { AdvancedRow("Weight change", String.format("%+.1f kg", it)) }
            Text("Next focus: ${report.focus}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ConnectedToolsScreen(
    health: ConnectedHealthSnapshot,
    settings: UserSettings,
    wearableStatus: String,
    onBack: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onRefreshHealth: () -> Unit,
    onWearableChanged: (Boolean) -> Unit,
    onExport: () -> Unit,
    onShareReport: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var confirmAccountDeletion by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Connected health & tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.HealthAndSafety, null, tint = FuelGreen)
                    Text("Health Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(health.status)
                    if (health.permissionsGranted) {
                        AdvancedRow("Steps today", health.stepsToday.toString())
                        AdvancedRow("Energy", "${health.activeCaloriesKcal.toInt()} kcal")
                        health.latestWeightKg?.let { AdvancedRow("Latest weight", String.format("%.1f kg", it)) }
                        health.sleepHours?.let { AdvancedRow("Sleep", String.format("%.1f h", it)) }
                        health.restingHeartRateBpm?.let { AdvancedRow("Resting HR", "$it bpm") }
                        OutlinedButton(onClick = onRefreshHealth, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CloudSync, null); Text(" Refresh") }
                    } else Button(onClick = onRequestHealthPermissions, enabled = health.available, modifier = Modifier.fillMaxWidth()) { Text("Connect Health Connect") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Watch, null)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) { Text("Wear OS sync", fontWeight = FontWeight.Bold); Text(wearableStatus, style = MaterialTheme.typography.bodySmall) }
                    Switch(settings.wearableSyncEnabled, onWearableChanged)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Home-screen widget", fontWeight = FontWeight.Bold)
                    Text("Add the MotionFuel widget from your Android launcher to see calories, steps and water at a glance.")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.Download, null); Text(" Export my data") }
                OutlinedButton(onClick = onShareReport, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.Share, null); Text(" Share weekly report") }
                OutlinedButton(onClick = { confirmAccountDeletion = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Delete MotionFuel account", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmAccountDeletion) {
        AlertDialog(
            onDismissRequest = { confirmAccountDeletion = false },
            title = { Text("Delete account permanently?") },
            text = { Text("Your Firebase account, profile and avatar will be deleted. Export your data first. This action cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmAccountDeletion = false; onDeleteAccount() }) { Text("Delete account", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmAccountDeletion = false }) { Text("Cancel") } },
        )
    }
}

fun weeklyReportShareIntent(report: WeeklyReport): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, "My MotionFuel weekly report")
    putExtra(Intent.EXTRA_TEXT, "MotionFuel weekly report\n${report.consistencyScore}% consistency\n${report.workouts} workouts • ${report.activeMinutes} active minutes\n${(report.distanceMeters / 1000).let { String.format("%.1f", it) }} km • ${report.exerciseCaloriesKcal.toInt()} exercise kcal\n${report.averageFoodCaloriesKcal.toInt()} kcal/day • ${report.proteinAverageG.toInt()} g protein/day\nNext focus: ${report.focus}")
}

@Composable
private fun AdvancedProgress(label: String, value: Int, target: Int) {
    Text("$label: $value / $target", fontWeight = FontWeight.SemiBold)
    LinearProgressIndicator(progress = { (value.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun AdvancedRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
