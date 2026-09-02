package com.ronitgandhi.motionfuel.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.algorithm.PrivacyZone
import com.ronitgandhi.motionfuel.domain.algorithm.PrivacyZoneMasker
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.TrendPoint
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.ui.components.BrandMark
import com.ronitgandhi.motionfuel.ui.components.EmptyInsightCard
import com.ronitgandhi.motionfuel.ui.components.InsightCard
import com.ronitgandhi.motionfuel.ui.components.RouteCanvas
import com.ronitgandhi.motionfuel.ui.components.RouteMap
import com.ronitgandhi.motionfuel.ui.components.formatDistance
import com.ronitgandhi.motionfuel.ui.components.formatDuration
import com.ronitgandhi.motionfuel.ui.components.formatPace
import com.ronitgandhi.motionfuel.ui.theme.FuelBlue
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelRose
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun TodayScreen(
    profile: UserProfile,
    workouts: List<WorkoutSummary>,
    nutrition: NutritionTotals,
    entries: List<NutritionEntry>,
    weather: WeatherContext,
    weatherStatus: String,
    insights: List<Insight>,
    settings: UserSettings,
    onStartWorkout: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenFood: () -> Unit,
) {
    val todayWorkouts = workouts.filter { isToday(it.startedAtMillis) }
    val exercise = todayWorkouts.sumOf { it.caloriesKcal }.toInt()
    val food = nutrition.caloriesKcal.toInt()
    val remaining = profile.dailyCalorieGoalKcal - food + exercise
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefreshWeather) { Icon(Icons.Rounded.Cloud, "Refresh weather") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Calories remaining", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(remaining.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = FuelBlue)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Maintenance", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${profile.maintenanceCaloriesKcal} kcal/day", fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        EquationValue("Goal", profile.dailyCalorieGoalKcal)
                        Text("−", style = MaterialTheme.typography.titleLarge)
                        EquationValue("Food", food)
                        Text("+", style = MaterialTheme.typography.titleLarge)
                        EquationValue("Exercise", exercise)
                        Text("=", style = MaterialTheme.typography.titleLarge)
                        EquationValue("Remaining", remaining)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Diary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onOpenFood) { Text("View diary") }
                    }
                    MealType.entries.forEach { meal ->
                        val calories = entries.filter { it.mealType == meal }.sumOf { it.caloriesKcal }.toInt()
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(meal.label())
                            Text("$calories kcal", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Steps", todayWorkouts.sumOf { it.steps }.toString(), Modifier.weight(1f))
                SummaryCard("Weather", "${weather.temperatureC.toInt()}°C", Modifier.weight(1f))
            }
            Text(weatherStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { insights.firstOrNull()?.let { InsightCard(it) } ?: EmptyInsightCard() }
        item {
            Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.size(8.dp))
                Text("Start walk or run", fontWeight = FontWeight.Bold)
            }
            if (workouts.isNotEmpty()) TextButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth()) { Text("View activity history") }
        }
    }
}

@Composable
private fun EquationValue(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ActivityScreen(workouts: List<WorkoutSummary>, settings: UserSettings, onStartWorkout: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Your locally saved walks and runs", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            FilledTonalButton(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.DirectionsRun, null)
                Spacer(Modifier.size(8.dp))
                Text("Track activity")
            }
        }
        if (workouts.isEmpty()) item { SummaryCard("No saved activities", "Start your first workout", Modifier.fillMaxWidth()) }
        else items(workouts, key = { it.id }) { workout ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(workout.type.name.lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Bold)
                            Text(SimpleDateFormat("EEE, d MMM • h:mm a", Locale.getDefault()).format(Date(workout.startedAtMillis)), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatDistance(workout.distanceMeters, settings.units == UnitSystem.IMPERIAL), fontWeight = FontWeight.Black)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        SmallMetric("Time", formatDuration(workout.durationSeconds))
                        SmallMetric("Pace", "${formatPace(workout.averagePaceSecPerKm)} /km")
                        SmallMetric("Energy", "${workout.caloriesKcal.toInt()} kcal")
                    }
                    if (workout.route.isNotEmpty()) RouteMap(workout.route, Modifier.fillMaxWidth().height(130.dp), workout.rejectedGpsPoints > 0)
                }
            }
        }
    }
}

@Composable
private fun SmallMetric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProgressScreen(
    nutritionHistory: List<NutritionEntry>,
    weightEntries: List<WeightEntry>,
    profile: UserProfile,
    insights: List<Insight>,
    onAddWeight: (Double) -> Unit,
) {
    var days by remember { mutableIntStateOf(7) }
    var showWeightDialog by remember { mutableStateOf(false) }
    val caloriePoints = remember(nutritionHistory, days, profile.dailyCalorieGoalKcal) { calorieTrend(nutritionHistory, days, profile.dailyCalorieGoalKcal) }
    val weightPoints = remember(weightEntries, days) { weightTrend(weightEntries, days) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Calories and weight trends", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { showWeightDialog = true }) { Text("Add weight") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { range -> FilterChip(selected = days == range, onClick = { days = range }, label = { Text("$range days") }) }
            }
        }
        item { TrendCard("Calories", "${caloriePoints.sumOf { it.value ?: 0.0 }.div(days).toInt()} kcal daily average", caloriePoints, "kcal", FuelBlue, showTarget = true) }
        item {
            val values = weightPoints.mapNotNull { it.value }
            val change = if (values.size >= 2) values.last() - values.first() else null
            TrendCard("Weight", change?.let { "${values.last().format(1)} kg • ${it.signed(1)} kg" } ?: "${profile.weightKg.format(1)} kg current", weightPoints, "kg", FuelGreen)
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Estimated maintenance", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${profile.maintenanceCaloriesKcal} kcal/day", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Based on your current profile and ${profile.activityLevel.label.lowercase()} activity.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { insights.firstOrNull()?.let { InsightCard(it) } ?: EmptyInsightCard() }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, null, tint = FuelGreen)
                    Spacer(Modifier.size(10.dp))
                    Text("Trends are wellness estimates and do not provide medical advice.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showWeightDialog) WeightDialog(profile.weightKg, { showWeightDialog = false }) {
        onAddWeight(it)
        showWeightDialog = false
    }
}

@Composable
private fun TrendCard(title: String, summary: String, points: List<TrendPoint>, suffix: String, color: Color, showTarget: Boolean = false) {
    var selected by remember(points) { mutableStateOf<TrendPoint?>(null) }
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(summary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            TrendBarChart(points, color, showTarget, Modifier.fillMaxWidth().height(190.dp)) { selected = it }
            selected?.let {
                val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(it.timestampMillis))
                Text("$date • ${it.value?.format(1) ?: "No entry"} $suffix${it.target?.let { target -> " • target ${target.toInt()}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            } ?: Text("Tap a bar for details", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendBarChart(points: List<TrendPoint>, color: Color, showTarget: Boolean, modifier: Modifier, onSelected: (TrendPoint) -> Unit) {
    val targetColor = MaterialTheme.colorScheme.error
    val values = points.mapNotNull { it.value } + if (showTarget) points.mapNotNull { it.target } else emptyList()
    val maximum = max(values.maxOrNull() ?: 1.0, 1.0)
    Canvas(modifier.pointerInput(points) {
        detectTapGestures { offset ->
            val index = (offset.x / (size.width / points.size.coerceAtLeast(1))).toInt().coerceIn(points.indices)
            onSelected(points[index])
        }
    }) {
        if (points.isEmpty()) return@Canvas
        val slot = size.width / points.size
        val barWidth = (slot * 0.62f).coerceAtLeast(3f)
        points.forEachIndexed { index, point ->
            point.value?.let { value ->
                val height = (value / maximum * size.height * 0.88).toFloat()
                drawRoundRect(color, Offset(index * slot + (slot - barWidth) / 2f, size.height - height), Size(barWidth, height))
            }
        }
        if (showTarget) points.mapNotNull { it.target }.lastOrNull()?.let { target ->
            val y = size.height - (target / maximum * size.height * 0.88).toFloat()
            drawLine(targetColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun WeightDialog(currentWeight: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf(currentWeight.format(1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add weight") },
        text = { OutlinedTextField(value, { value = it }, label = { Text("Weight (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) },
        confirmButton = { TextButton(onClick = { value.toDoubleOrNull()?.let(onSave) }, enabled = value.toDoubleOrNull()?.let { it in 30.0..350.0 } == true) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ProfileScreen(
    profile: UserProfile,
    settings: UserSettings,
    onUnitsChanged: (UnitSystem) -> Unit,
    onRouteBackupChanged: (Boolean) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onDeleteData: () -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val originalRoute = remember { privacyRoute() }
    val masked = remember { PrivacyZoneMasker.mask(originalRoute, listOf(PrivacyZone(originalRoute.first(), 95.0))) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(52.dp))
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(profile.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SettingsCard("Goals") {
                Text("Maintenance ${profile.maintenanceCaloriesKcal} kcal/day", fontWeight = FontWeight.Bold)
                Text("Daily goal ${profile.dailyCalorieGoalKcal} kcal/day")
                Text("${profile.weightKg.format(1)} kg • ${profile.heightCm.format(0)} cm • ${profile.activityLevel.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsCard("Privacy & cloud") {
                SettingToggle("Back up detailed routes", if (settings.routeBackupEnabled) "Masked route may sync" else "Detailed routes remain local", settings.routeBackupEnabled, onRouteBackupChanged)
                RouteCanvas(masked, Modifier.fillMaxWidth().height(125.dp))
            }
        }
        item {
            SettingsCard("Display") {
                SettingToggle("Dark theme", "Applies immediately", settings.darkTheme, onDarkThemeChanged)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onUnitsChanged(UnitSystem.METRIC) }, enabled = settings.units != UnitSystem.METRIC, modifier = Modifier.weight(1f)) { Text("Metric") }
                    OutlinedButton({ onUnitsChanged(UnitSystem.IMPERIAL) }, enabled = settings.units != UnitSystem.IMPERIAL, modifier = Modifier.weight(1f)) { Text("Imperial") }
                }
            }
        }
        item {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteForever, null, tint = FuelRose)
                Spacer(Modifier.size(8.dp))
                Text("Delete local data", color = FuelRose)
            }
        }
        item {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Logout, null)
                Spacer(Modifier.size(8.dp))
                Text("Sign out of Firebase")
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete local data?") },
        text = { Text("Saved workouts, nutrition and weight entries on this device will be removed.") },
        confirmButton = { TextButton(onClick = { onDeleteData(); confirmDelete = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange)
    }
}

private fun calorieTrend(entries: List<NutritionEntry>, days: Int, target: Int): List<TrendPoint> = dayStarts(days).map { start ->
    TrendPoint(start, entries.filter { it.consumedAtMillis in start until start + DAY }.sumOf { it.caloriesKcal }, target.toDouble())
}

private fun weightTrend(entries: List<WeightEntry>, days: Int): List<TrendPoint> = dayStarts(days).map { start ->
    TrendPoint(start, entries.filter { it.recordedAtMillis in start until start + DAY }.maxByOrNull { it.recordedAtMillis }?.weightKg)
}

private fun dayStarts(days: Int): List<Long> {
    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    return (days - 1 downTo 0).map { today - it * DAY }
}

private fun MealType.label() = name.lowercase().replaceFirstChar(Char::uppercase)
private fun Double.format(decimals: Int) = "%.$decimals".plus("f").format(Locale.US, this)
private fun Double.signed(decimals: Int) = (if (this >= 0) "+" else "") + format(decimals)
private fun isToday(time: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(time)) == SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

private fun privacyRoute(): List<GeoPoint> {
    val now = System.currentTimeMillis()
    return List(18) { index -> GeoPoint(-37.8136 + index * 0.00008, 144.9631 + index * 0.00009, timestampMillis = now + index * 5_000) }
}

private const val DAY = 86_400_000L
