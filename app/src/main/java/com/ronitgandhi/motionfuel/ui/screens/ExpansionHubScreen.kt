package com.ronitgandhi.motionfuel.ui.screens

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.ronitgandhi.motionfuel.MotionFuelViewModel
import com.ronitgandhi.motionfuel.R
import com.ronitgandhi.motionfuel.domain.algorithm.ExpansionEngine
import com.ronitgandhi.motionfuel.domain.model.RecipeIngredient
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.integration.NutritionLabelScanner
import com.ronitgandhi.motionfuel.share.ActivityShareImage
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ExpansionSection(val label: String) { FOOD("Food"), TRAINING("Training"), COMMUNITY("Community"), DATA("Data") }

@Composable
fun ExpansionHubScreen(viewModel: MotionFuelViewModel, profile: UserProfile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEverydayTools by remember { mutableStateOf(false) }
    if (showEverydayTools) {
        EverydayToolsScreen(viewModel, profile) { showEverydayTools = false }
        return
    }
    val featureStore = remember(profile.userId) { com.ronitgandhi.motionfuel.data.features.FeatureStore(context, profile.userId) }
    val featureData by featureStore.data.collectAsState()

    val foods by viewModel.savedFoods.collectAsState()
    val recommendations by viewModel.mealRecommendations.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val plans by viewModel.plannedWorkouts.collectAsState()
    val routes by viewModel.plannedRoutes.collectAsState()
    val challenges by viewModel.challenges.collectAsState()
    val weights by viewModel.allWeightEntries.collectAsState()
    val workouts by viewModel.workouts.collectAsState()
    val health by viewModel.connectedHealth.collectAsState()
    val safetyUrl by viewModel.safetyShareUrl.collectAsState()
    val standings by viewModel.challengeStandings.collectAsState()
    val filteredRecommendations = recommendations.filter { recommendation ->
        val list = featureData.optJSONArray("foodMetadata") ?: org.json.JSONArray()
        val metadata = (0 until list.length()).map { list.getJSONObject(it) }.find { it.optString("id") == recommendation.foodId }
        com.ronitgandhi.motionfuel.domain.algorithm.FoodPlanning.allowed(
            metadata?.optString("tags").orEmpty().split(",").toSet(), metadata?.optString("allergens").orEmpty().split(",").toSet(),
            featureData.optString("diet", "Any"), featureData.optString("excluded").split(",").filter(String::isNotBlank).toSet(), metadata?.optBoolean("verified") == true)
    }
    var section by remember { mutableStateOf(ExpansionSection.FOOD) }
    var recipeDialog by remember { mutableStateOf(false) }
    var workoutDialog by remember { mutableStateOf(false) }
    var routeDialog by remember { mutableStateOf(false) }
    var challengeDialog by remember { mutableStateOf(false) }
    var joinChallengeDialog by remember { mutableStateOf(false) }
    var targetWeight by remember { mutableStateOf(profile.weightKg.toString()) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<NutritionTotals?>(null) }
    var pendingScanFile by remember { mutableStateOf<File?>(null) }
    var celebrating by remember { mutableStateOf(false) }
    val labelScanner = remember { NutritionLabelScanner(context) }
    val labelCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = pendingScanFile
        if (captured && file != null) scope.launch {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            scanMessage = "Reading nutrition label…"
            labelScanner.scan(uri).onSuccess { values ->
                scanResult = values
                scanMessage = "Detected per label: ${values.caloriesKcal.toInt()} kcal • ${values.proteinG} g protein • ${values.carbohydratesG} g carbs • ${values.fatG} g fat"
            }.onFailure { scanMessage = it.message }
            file.delete(); pendingScanFile = null
        } else { file?.delete(); pendingScanFile = null }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.setSmartReminders(true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column { Text(stringResource(R.string.smart_planning), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(stringResource(R.string.smart_planning_subtitle), style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpansionSection.entries.forEach { item -> FilterChip(selected = section == item, onClick = { section = item }, label = { Text(item.label) }) }
            }
        }
        item { OutlinedButton(onClick = { showEverydayTools = true }, modifier = Modifier.fillMaxWidth()) { Text("Everyday tools • pantry, sync and training") } }
        when (section) {
            ExpansionSection.FOOD -> {
                item { FeatureTitle(Icons.Rounded.Restaurant, "Smart meal recommendations", "Ranked against today's remaining calories and protein") }
                if (filteredRecommendations.isEmpty()) item { InfoCard("Save foods with confirmed ingredient metadata matching your dietary preferences to receive suggestions.") }
                else items(filteredRecommendations, key = { it.foodId }) { item -> InfoCard("${item.name} • ${item.caloriesKcal.toInt()} kcal\n${item.reason}") }
                item {
                    Button(onClick = {
                        val directory = File(context.cacheDir, "label_scans").apply { mkdirs() }
                        val file = File.createTempFile("nutrition_", ".jpg", directory)
                        pendingScanFile = file
                        labelCamera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CameraAlt, null); Text(" Scan nutrition label") }
                }
                scanMessage?.let { item { InfoCard(it) } }
                scanResult?.let { values -> item { OutlinedButton(onClick = { viewModel.addManualFood("Scanned food", values.caloriesKcal, values.proteinG, values.carbohydratesG, values.fatG, MealType.SNACK, null); scanMessage = "Scanned food saved and added to Snacks."; scanResult = null }, modifier = Modifier.fillMaxWidth()) { Text("Save scanned food") } } }
                item { FeatureTitle(Icons.Rounded.Restaurant, "Recipe builder", "Combine saved foods and scale nutrition by servings") }
                item { OutlinedButton(onClick = { recipeDialog = true }, enabled = foods.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Create recipe") } }
                items(recipes, key = { it.id }) { recipe ->
                    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(recipe.name, fontWeight = FontWeight.Bold); Text("${recipe.caloriesPerServing.toInt()} kcal • ${recipe.proteinPerServing.toInt()} g protein per serving") }
                        IconButton(onClick = { viewModel.deleteRecipe(recipe.id) }) { Icon(Icons.Rounded.Delete, "Delete recipe") }
                    } }
                }
            }
            ExpansionSection.TRAINING -> {
                item { FeatureTitle(Icons.Rounded.Schedule, "Workout planner", "Schedule a walk or run and track completion") }
                item { Button(onClick = { workoutDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Schedule workout") } }
                items(plans, key = { it.id }) { plan -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("${plan.type.name.lowercase().replaceFirstChar(Char::uppercase)} • ${plan.targetDistanceMeters.toInt()} m • ${plan.targetDurationMinutes} min", fontWeight = FontWeight.Bold); Text(DateFormat.getDateTimeInstance().format(Date(plan.scheduledAtMillis))); Row { TextButton(onClick = { viewModel.setWorkoutPlanCompleted(plan, !plan.completed) }) { Text(if (plan.completed) "Mark pending" else "Mark completed") }; TextButton(onClick = { viewModel.deleteWorkoutPlan(plan.id) }) { Text("Delete") } } } } }
                item { FeatureTitle(Icons.Rounded.Route, "Route planner and offline routes", "Creates a distance-matched loop and stores it locally") }
                item { OutlinedButton(onClick = { routeDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Plan route") } }
                items(routes, key = { it.id }) { route -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("${route.name} • ${(route.targetDistanceMeters / 1000).format1()} km", fontWeight = FontWeight.Bold); OfflineRoutePreview(route.points, Modifier.fillMaxWidth().height(150.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Polyline available offline", style = MaterialTheme.typography.labelSmall); TextButton(onClick = { viewModel.deleteRoutePlan(route.id) }) { Text("Delete") } } } } }
                item { FeatureTitle(Icons.Rounded.Map, "Exercise-zone analysis", "Pace zones calculated from saved sessions") }
                val zones = ExpansionEngine.paceZones(workouts.mapNotNull { it.averagePaceSecPerKm })
                item { InfoCard(if (zones.isEmpty()) "Complete a paced workout to unlock zones." else zones.joinToString(" • ") { "${it.label}: ${it.seconds}" }) }
                if (health.restingHeartRateBpm != null) item { InfoCard("Heart-rate ranges: ${ExpansionEngine.heartRateZoneRanges(profile.age).joinToString(" • ")}") }
                item { FeatureTitle(Icons.Rounded.Map, "Progress prediction", "A cautious projection from recorded weight history") }
                item { OutlinedTextField(targetWeight, { targetWeight = it }, label = { Text("Target weight (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) }
                item { InfoCard(ExpansionEngine.predictWeight(weights, targetWeight.toDoubleOrNull() ?: 0.0).summary) }
            }
            ExpansionSection.COMMUNITY -> {
                item { FeatureTitle(Icons.Rounded.Security, "Live safety sharing", "Share a private, time-limited workout location link") }
                item { Button(onClick = { viewModel.startSafetyShare() }, modifier = Modifier.fillMaxWidth()) { Text("Start 60-minute safety share") } }
                safetyUrl?.let { url -> item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Safety sharing is active for 60 minutes", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Follow my MotionFuel workout: $url"), "Share safety link")) }) { Text("Share link") }; OutlinedButton(onClick = viewModel::stopSafetyShare) { Text("Stop") } } } } } }
                item { FeatureTitle(Icons.Rounded.Groups, "Challenges and leaderboard", "Create a seven-day distance, steps or consistency goal") }
                item { OutlinedButton(onClick = { challengeDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Create challenge") } }
                item { TextButton(onClick = { joinChallengeDialog = true }) { Text("Join with challenge code") } }
                items(challenges, key = { it.id }) { challenge -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(challenge.title, fontWeight = FontWeight.Bold); Text("Goal: ${challenge.target.toInt()} ${challenge.metric}"); standings[challenge.id].orEmpty().take(5).forEachIndexed { index, standing -> Text("${index + 1}. ${standing.displayName} — ${standing.score.toInt()}") }; TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Join my MotionFuel challenge with code ${challenge.id}"), "Invite friends")) }) { Text("Share invite") } } } }
                item { FeatureTitle(Icons.Rounded.Celebration, "Personal-best celebrations", "Share your strongest saved result") }
                if (celebrating) item { Text("🎉  NEW PERSONAL BEST  🎉", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedButton(onClick = {
                    val best = workouts.maxByOrNull { it.distanceMeters }
                    celebrating = true
                    if (best != null) scope.launch { runCatching { withContext(Dispatchers.IO) { ActivityShareImage.createShareIntent(context, best, UnitSystem.METRIC, false) } }.onSuccess { context.startActivity(Intent.createChooser(it, "Share achievement")) } }
                }, modifier = Modifier.fillMaxWidth()) { Text("Celebrate and share") } }
            }
            ExpansionSection.DATA -> {
                item { FeatureTitle(Icons.Rounded.Notifications, "Intelligent notifications", "Inactivity, meal-gap, weather and hydration reminders") }
                item { Button(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setSmartReminders(true) }, modifier = Modifier.fillMaxWidth()) { Text("Enable smart reminders") } }
                item { FeatureTitle(Icons.Rounded.Security, "Data-source management", "Understand where each metric originates") }
                item { InfoCard("Workouts: MotionFuel phone sensors\nFood and water: MotionFuel local diary\nSteps, energy, sleep, heart rate and imported weight: ${if (health.permissionsGranted) "Health Connect" else "Health Connect not connected"}") }
                item { FeatureTitle(Icons.Rounded.Map, "Accessibility and localisation", "Large text, high contrast and English/Hindi preferences") }
                item { OutlinedButton(onClick = { viewModel.toggleAccessibleDisplay() }, modifier = Modifier.fillMaxWidth()) { Text("Toggle accessible display") } }
                item { OutlinedButton(onClick = { viewModel.toggleLanguage() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.switch_language)) } }
            }
        }
    }
    if (recipeDialog) RecipeDialog(foods, { recipeDialog = false }) { name, servings, ingredients -> viewModel.saveRecipe(name, servings, ingredients); recipeDialog = false }
    if (workoutDialog) WorkoutPlanDialog({ workoutDialog = false }) { type, distance, duration -> viewModel.scheduleWorkout(type, System.currentTimeMillis() + 86_400_000L, distance, duration); workoutDialog = false }
    if (routeDialog) SimpleInputDialog("Plan offline route", "Route name", "Distance (km)", { routeDialog = false }) { name, value -> viewModel.createRoutePlan(name, value * 1000); routeDialog = false }
    if (challengeDialog) SimpleInputDialog("Create weekly challenge", "Challenge title", "Distance target (km)", { challengeDialog = false }) { name, value -> viewModel.createChallenge(name, "km", value, FirebaseAuth.getInstance().currentUser?.uid.orEmpty()); challengeDialog = false }
    if (joinChallengeDialog) JoinChallengeDialog({ joinChallengeDialog = false }) { code -> viewModel.joinChallenge(code); joinChallengeDialog = false }
}

@Composable private fun FeatureTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.size(10.dp)); Column { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun InfoCard(text: String) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(text, Modifier.padding(14.dp)) } }

@Composable private fun RecipeDialog(foods: List<com.ronitgandhi.motionfuel.domain.model.SavedFood>, onDismiss: () -> Unit, onSave: (String, Double, List<RecipeIngredient>) -> Unit) {
    var name by remember { mutableStateOf("") }; var servings by remember { mutableStateOf("1") }; var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Build recipe") }, text = { LazyColumn { item { OutlinedTextField(name, { name = it }, label = { Text("Recipe name") }); OutlinedTextField(servings, { servings = it }, label = { Text("Servings") }) }; items(foods) { food -> Row(Modifier.fillMaxWidth().clickable { selected = if (food.id in selected) selected - food.id else selected + food.id }.padding(8.dp)) { Text(if (food.id in selected) "✓ " else "○ "); Text(food.name) } } } }, confirmButton = { TextButton(onClick = { onSave(name, servings.toDoubleOrNull() ?: 1.0, foods.filter { it.id in selected }.map { RecipeIngredient(it.name, 1.0, it.caloriesKcal, it.proteinG, it.carbohydratesG, it.fatG) }) }, enabled = name.isNotBlank() && selected.isNotEmpty()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun WorkoutPlanDialog(onDismiss: () -> Unit, onSave: (WorkoutType, Double, Int) -> Unit) { var type by remember { mutableStateOf(WorkoutType.WALK) }; var distance by remember { mutableStateOf("3") }; var duration by remember { mutableStateOf("30") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Schedule for tomorrow") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { WorkoutType.entries.forEach { FilterChip(type == it, { type = it }, { Text(it.name.lowercase().replaceFirstChar(Char::uppercase)) }); Spacer(Modifier.size(6.dp)) } }; OutlinedTextField(distance, { distance = it }, label = { Text("Distance (km)") }); OutlinedTextField(duration, { duration = it }, label = { Text("Duration (minutes)") }) } }, confirmButton = { TextButton(onClick = { onSave(type, (distance.toDoubleOrNull() ?: 0.0) * 1000, duration.toIntOrNull() ?: 0) }) { Text("Schedule") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

@Composable private fun SimpleInputDialog(title: String, firstLabel: String, secondLabel: String, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) { var name by remember { mutableStateOf("") }; var value by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text(firstLabel) }); OutlinedTextField(value, { value = it }, label = { Text(secondLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } }, confirmButton = { TextButton(onClick = { onSave(name, value.toDoubleOrNull() ?: 0.0) }, enabled = name.isNotBlank() && (value.toDoubleOrNull() ?: 0.0) > 0) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
@Composable private fun JoinChallengeDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) { var code by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Join challenge") }, text = { OutlinedTextField(code, { code = it.trim() }, label = { Text("Challenge code") }) }, confirmButton = { TextButton(onClick = { onJoin(code) }, enabled = code.isNotBlank()) { Text("Join") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
private fun Double.format1() = String.format("%.1f", this)

@Composable private fun OfflineRoutePreview(points: List<com.ronitgandhi.motionfuel.domain.model.GeoPoint>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
        fun project(point: com.ronitgandhi.motionfuel.domain.model.GeoPoint) = Offset(
            (((point.longitude - minLon) / (maxLon - minLon).coerceAtLeast(1e-8)) * size.width).toFloat(),
            (size.height - ((point.latitude - minLat) / (maxLat - minLat).coerceAtLeast(1e-8) * size.height)).toFloat(),
        )
        points.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFF56E39F), project(a), project(b), strokeWidth = 8f) }
        drawCircle(Color(0xFF62B6FF), 11f, project(points.first())); drawCircle(Color(0xFFFFB45C), 11f, project(points.last()))
    }
}
