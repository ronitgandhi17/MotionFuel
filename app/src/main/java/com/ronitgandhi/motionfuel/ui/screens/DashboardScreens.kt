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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.algorithm.NutritionMath
import com.ronitgandhi.motionfuel.domain.algorithm.PrivacyZone
import com.ronitgandhi.motionfuel.domain.algorithm.PrivacyZoneMasker
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.Sex
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.core.maps.RouteMap
import com.ronitgandhi.motionfuel.ui.components.BrandMark
import com.ronitgandhi.motionfuel.ui.components.EmptyInsightCard
import com.ronitgandhi.motionfuel.ui.components.InsightCard
import com.ronitgandhi.motionfuel.ui.components.MacroProgress
import com.ronitgandhi.motionfuel.ui.components.MetricCard
import com.ronitgandhi.motionfuel.ui.components.SectionHeader
import com.ronitgandhi.motionfuel.ui.components.formatDistance
import com.ronitgandhi.motionfuel.ui.components.formatDuration
import com.ronitgandhi.motionfuel.ui.components.formatPace
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelNavy
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelRose
import com.ronitgandhi.motionfuel.ui.theme.FuelSky
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    profile: UserProfile?,
    totals: NutritionTotals,
    entries: List<NutritionEntry>,
    workouts: List<WorkoutSummary>,
    weather: WeatherContext?,
    weatherStatus: String,
    insights: List<Insight>,
    settings: UserSettings,
    onStartWorkout: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenFood: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val imperial = settings.units == UnitSystem.IMPERIAL
    val todaysWorkouts = workouts.filter { isToday(it.startedAtMillis) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "MotionFuel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Hi, ${profile?.displayName ?: "there"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        item { WeatherCard(weather, weatherStatus, imperial, onRefreshWeather) }
        item { CaloriesCard(profile, totals, onOpenProgress) }
        item {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    SectionHeader("Today's macros", action = "Log food", onAction = onOpenFood)
                    MacroProgress("Protein", totals.proteinG, profile?.proteinTargetG ?: 120.0, FuelSky)
                    MacroProgress("Carbohydrate", totals.carbohydratesG, 260.0, FuelOrange)
                    MacroProgress("Fat", totals.fatG, 70.0, FuelRose)
                }
            }
        }
        item {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Start workout", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenFood,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Restaurant, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Log food", fontWeight = FontWeight.Bold)
            }
        }
        item {
            SectionHeader("Today's workouts")
            Spacer(Modifier.height(10.dp))
            if (todaysWorkouts.isEmpty()) {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DirectionsRun, contentDescription = null, tint = FuelGreen)
                        Spacer(Modifier.size(12.dp))
                        Text("No workouts yet today", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    todaysWorkouts.forEach { workout ->
                        Card(shape = RoundedCornerShape(22.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(workoutTypeLabel(workout), fontWeight = FontWeight.Bold)
                                    Text(
                                        "${formatDistance(workout.distanceMeters, imperial)} • ${formatDuration(workout.durationSeconds)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("${workout.caloriesKcal.roundToInt()} kcal", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Adaptive insight")
            Spacer(Modifier.height(10.dp))
            insights.firstOrNull()?.let { InsightCard(it) } ?: EmptyInsightCard()
        }
    }
}

@Composable
private fun WeatherCard(
    weather: WeatherContext?,
    weatherStatus: String,
    imperial: Boolean,
    onRefreshWeather: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "LOCAL CONDITIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (weather != null) {
                    val tempText = if (imperial) {
                        "${(weather.temperatureC * 9 / 5 + 32).roundToInt()}°F"
                    } else {
                        "${weather.temperatureC.roundToInt()}°C"
                    }
                    Text(tempText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Text(if (weather.isRaining) "Rain in your area" else "Clear conditions")
                    Text(
                        "${weather.humidityPercent}% humidity • ${weather.windSpeedKph.roundToInt()} km/h wind",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("—°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                }
                Text(
                    weatherStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (weather == null && weatherStatus.contains("Updating", ignoreCase = true)) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    IconButton(onClick = onRefreshWeather) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh weather")
                    }
                }
            }
        }
    }
}

@Composable
private fun CaloriesCard(
    profile: UserProfile?,
    totals: NutritionTotals,
    onOpenProgress: () -> Unit,
) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Calories today", action = "Progress", onAction = onOpenProgress)
            if (profile == null) {
                Text(
                    "Complete your profile to see your calorie goal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val goal = profile.dailyCalorieGoal
                val percent = NutritionMath.percentOfGoal(totals.caloriesKcal, goal)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${totals.caloriesKcal.roundToInt()}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "/ $goal kcal goal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = FuelGreen,
                    trackColor = FuelGreen.copy(alpha = 0.15f),
                )
                Text(
                    "Estimated maintenance ${profile.maintenanceCalories} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ActivityScreen(workouts: List<WorkoutSummary>, settings: UserSettings, onStartWorkout: () -> Unit) {
    val imperial = settings.units == UnitSystem.IMPERIAL
    val ordered = workouts.sortedByDescending { it.startedAtMillis }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "Your saved walks and runs, most recent first.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FilledTonalButton(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.DirectionsRun, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Start workout", fontWeight = FontWeight.Bold)
            }
        }
        if (ordered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.DirectionsRun, contentDescription = null, modifier = Modifier.size(44.dp), tint = FuelGreen)
                        Spacer(Modifier.height(12.dp))
                        Text("No saved workouts", fontWeight = FontWeight.Bold)
                        Text(
                            "Start a walk or run to record your first activity.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            items(ordered, key = { it.id }) { workout ->
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = FuelGreen.copy(alpha = 0.15f)) {
                                Icon(
                                    Icons.Rounded.DirectionsRun,
                                    contentDescription = null,
                                    tint = FuelGreen,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(workoutTypeLabel(workout), fontWeight = FontWeight.Bold)
                                Text(
                                    SimpleDateFormat("EEE, d MMM • h:mm a", Locale.getDefault()).format(Date(workout.startedAtMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                formatDistance(workout.distanceMeters, imperial),
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            SmallMetric("Time", formatDuration(workout.durationSeconds))
                            SmallMetric("Pace", "${formatPace(workout.averagePaceSecPerKm)} /km")
                            SmallMetric("Energy", "${workout.caloriesKcal.roundToInt()} kcal")
                            SmallMetric("Climb", "${workout.elevationGainMeters.roundToInt()} m")
                        }
                        if (workout.route.size >= 2) {
                            RouteMap(
                                route = workout.route,
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                showRejectedBadge = workout.rejectedGpsPoints > 0,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isToday(timeMillis: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = timeMillis }
    return now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun workoutTypeLabel(workout: WorkoutSummary): String =
    workout.type.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun SmallMetric(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = FuelGreen)
                Spacer(Modifier.size(10.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    settings: UserSettings,
    latestRoute: List<GeoPoint>,
    onUnitsChanged: (UnitSystem) -> Unit,
    onRouteBackupChanged: (Boolean) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onEditProfile: (Int, Sex, Double) -> Unit,
    onDeleteData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    val imperial = settings.units == UnitSystem.IMPERIAL
    var showEdit by remember { mutableStateOf(false) }
    var confirmDeleteData by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var ageText by remember(profile) { mutableStateOf((profile?.age ?: 30).toString()) }
    var sex by remember(profile) { mutableStateOf(profile?.sex ?: Sex.MALE) }
    var heightText by remember(profile) { mutableStateOf((profile?.heightCm ?: 175.0).roundToInt().toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.displayName ?: "Your profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    profile?.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            SettingsCard("Account", Icons.Rounded.Person) {
                profileFactRow("Age", "${profile?.age ?: "—"}")
                profileFactRow("Sex", profile?.sex?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—")
                val h = profile?.heightCm
                profileFactRow("Height", if (h == null) "—" else if (imperial) formatHeightImperial(h) else "${h.roundToInt()} cm")
                val w = profile?.weightKg
                profileFactRow("Weight", if (w == null) "—" else if (imperial) "${(w * 2.2046226218).roundToInt()} lb" else "${w.roundToInt()} kg")
                profileFactRow("Activity", profile?.activityLevel?.label ?: "—")
                profileFactRow("Maintenance", "${profile?.maintenanceCalories ?: 0} kcal")
                TextButton(onClick = { showEdit = !showEdit }) { Text(if (showEdit) "Close" else "Edit basics") }
                if (showEdit) {
                    OutlinedTextField(ageText, { ageText = it }, label = { Text("Age") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = sex == Sex.MALE, onClick = { sex = Sex.MALE }, label = { Text("Male") })
                        FilterChip(selected = sex == Sex.FEMALE, onClick = { sex = Sex.FEMALE }, label = { Text("Female") })
                    }
                    OutlinedTextField(heightText, { heightText = it }, label = { Text("Height (cm)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onEditProfile(ageText.toIntOrNull() ?: (profile?.age ?: 30), sex, heightText.toDoubleOrNull() ?: (profile?.heightCm ?: 175.0))
                            showEdit = false
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Save") }
                }
            }
        }
        item {
            SettingsCard("Preferences", Icons.Rounded.Straighten) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Units", fontWeight = FontWeight.SemiBold)
                        Text("Distance and weight display", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilterChip(selected = settings.units == UnitSystem.METRIC, onClick = { onUnitsChanged(UnitSystem.METRIC) }, label = { Text("Metric") })
                    Spacer(Modifier.size(8.dp))
                    FilterChip(selected = settings.units == UnitSystem.IMPERIAL, onClick = { onUnitsChanged(UnitSystem.IMPERIAL) }, label = { Text("Imperial") })
                }
                HorizontalDivider()
                SettingToggle("Dark theme", "Use the dark colour scheme", settings.darkTheme, onDarkThemeChanged)
            }
        }
        item {
            SettingsCard("Privacy", Icons.Rounded.Lock) {
                SettingToggle(
                    "Route backup",
                    "When on, your detailed route is uploaded to your private cloud. Off keeps routes on-device only.",
                    settings.routeBackupEnabled,
                    onRouteBackupChanged,
                )
                if (latestRoute.size >= 2) {
                    HorizontalDivider()
                    Text("Latest route (home area masked)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val masked = PrivacyZoneMasker.mask(
                        latestRoute,
                        listOf(PrivacyZone(centre = latestRoute.first(), radiusMeters = 150.0)),
                    )
                    RouteMap(route = masked, modifier = Modifier.fillMaxWidth().height(150.dp))
                }
            }
        }
        item {
            SettingsCard("Data & account", Icons.Rounded.DeleteForever) {
                OutlinedButton(onClick = { confirmDeleteData = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete local data")
                }
                Button(
                    onClick = { confirmDeleteAccount = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Delete account")
                }
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Logout, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Sign out")
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
    if (confirmDeleteData) {
        AlertDialog(
            onDismissRequest = { confirmDeleteData = false },
            title = { Text("Delete local data?") },
            text = { Text("This clears all workouts, meals and weights stored on this device. Cloud data is not affected.") },
            confirmButton = { TextButton(onClick = { confirmDeleteData = false; onDeleteData() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDeleteData = false }) { Text("Cancel") } },
        )
    }
    if (confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAccount = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your Firebase account and all cloud data, and clears this device. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDeleteAccount = false; onDeleteAccount() }) { Text("Delete account") } },
            dismissButton = { TextButton(onClick = { confirmDeleteAccount = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun profileFactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatHeightImperial(cm: Double): String {
    val totalInches = (cm / 2.54).roundToInt()
    return "${totalInches / 12}' ${totalInches % 12}\""
}
