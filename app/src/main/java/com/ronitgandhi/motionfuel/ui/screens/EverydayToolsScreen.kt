package com.ronitgandhi.motionfuel.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.BuildConfig
import com.ronitgandhi.motionfuel.MotionFuelApplication
import com.ronitgandhi.motionfuel.MotionFuelViewModel
import com.ronitgandhi.motionfuel.data.features.CloudSync
import com.ronitgandhi.motionfuel.data.features.FeatureStore
import com.ronitgandhi.motionfuel.domain.algorithm.FoodPlanning
import com.ronitgandhi.motionfuel.domain.algorithm.TrainingAnalysis
import com.ronitgandhi.motionfuel.domain.algorithm.GeoMath
import com.ronitgandhi.motionfuel.domain.model.*
import com.ronitgandhi.motionfuel.share.CoachReport
import com.ronitgandhi.motionfuel.share.GpxCodec
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun EverydayToolsScreen(viewModel: MotionFuelViewModel, profile: UserProfile, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MotionFuelApplication
    val store = remember(profile.userId) { FeatureStore(context, profile.userId) }
    val data by store.data.collectAsState()
    val sync = remember(profile.userId) { CloudSync(app, profile.userId, store) }
    val scope = rememberCoroutineScope()
    val workouts by viewModel.workouts.collectAsState()
    val foods by viewModel.savedFoods.collectAsState()
    val entries by viewModel.allNutritionEntries.collectAsState()
    val weights by viewModel.allWeightEntries.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val mealPlan by viewModel.tomorrowMealPlan.collectAsState()
    var tab by remember { mutableStateOf("Food") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var pantryName by remember { mutableStateOf("") }
    var pantryAmount by remember { mutableStateOf("1") }
    var pantryExpiry by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var recipeId by remember { mutableStateOf<String?>(null) }
    var recipeServings by remember { mutableStateOf("1") }
    var selectedFood by remember { mutableStateOf<String?>(null) }
    var selectedEntry by remember { mutableStateOf<String?>(null) }
    var nutrient by remember { mutableStateOf("Fibre (g)") }
    var nutrientAmount by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var diagnosticAttachment by remember { mutableStateOf(false) }
    var exportRoute by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    fun task(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = block() } catch (e: Exception) { message = e.message ?: "Action could not be completed." }
            finally { busy = false }
        }
    }
    fun runSync(choice: String? = null) = task {
        val result = sync.sync(choice)
        conflict = result == "CONFLICT"
        if (conflict) "Both devices have changes. Choose which complete copy to keep." else result
    }
    val importGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) task {
            val points = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use(GpxCodec::read) ?: error("Cannot open GPX file.") }
            val route = PlannedRoute(UUID.randomUUID().toString(), "Imported GPX ${LocalDate.now()}", points.zipWithNext().sumOf { GeoMath.distanceMeters(it.first, it.second) }, points, System.currentTimeMillis())
            app.repository.savePlannedRoute(route)
            "GPX imported. Open Smart Planning → Training to view it."
        }
    }
    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) task {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(GpxCodec.write("MotionFuel route", exportRoute)) } ?: error("Could not write GPX.") }
            "GPX exported."
        }
    }
    BackHandler { if (!busy) onBack() }
    fun rows(key: String): List<JSONObject> {
        val a = data.optJSONArray(key) ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack, enabled = !busy) { Text("Back") }; Text("Everyday tools", style = MaterialTheme.typography.headlineMedium) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Food", "Training", "Privacy", "Feedback").forEach { label -> FilterChip(tab == label, { if (!busy) tab = label }, { Text(label) }) } } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        when (tab) {
            "Food" -> {
                item { ToolCard("Pantry inventory") {
                    Text("Track quantities in servings of the same food; matching uses its name.")
                    OutlinedTextField(pantryName, { pantryName = it.take(100) }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pantryAmount, { pantryAmount = it }, label = { Text("Servings available") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pantryExpiry, { pantryExpiry = it }, label = { Text("Expiry date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { runCatching {
                        val amount = pantryAmount.toDouble(); require(amount.isFinite() && amount in 0.01..100000.0 && pantryName.isNotBlank())
                        val date = LocalDate.parse(pantryExpiry)
                        store.putItem("pantry", JSONObject().put("id", UUID.randomUUID().toString()).put("name", pantryName.trim()).put("amount", amount).put("expiry", date.toString()))
                        pantryName = ""
                    }.onFailure { message = "Enter a name, positive servings, and a valid expiry date." } }) { Text("Add to pantry") }
                } }
                items(rows("pantry"), key = { "pantry_" + it.getString("id") }) { item -> ToolCard(item.getString("name")) {
                    val expiry = LocalDate.parse(item.getString("expiry"))
                    Text("${item.getDouble("amount")} servings • expires $expiry")
                    if (!expiry.isAfter(LocalDate.now().plusDays(2))) Text(if (expiry.isBefore(LocalDate.now())) "Expired" else "Use soon", color = MaterialTheme.colorScheme.error)
                    Row { TextButton(onClick = { val left = item.getDouble("amount") - 1; if (left <= 0) store.removeItem("pantry", item.getString("id")) else store.putItem("pantry", JSONObject(item.toString()).put("amount", left)) }) { Text("Use 1 serving") }; TextButton(onClick = { store.removeItem("pantry", item.getString("id")) }) { Text("Remove") } }
                } }
                item { ToolCard("Automatic grocery list") {
                    Text("Includes tomorrow's meal plan and the optional recipe below, minus unexpired pantry servings.")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(recipeId == null, { recipeId = null }, { Text("No recipe") })
                        recipes.forEach { recipe -> FilterChip(recipeId == recipe.id, { recipeId = recipe.id }, { Text(recipe.name) }) }
                    }
                    OutlinedTextField(recipeServings, { recipeServings = it }, label = { Text("Recipe servings to prepare") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Button(onClick = {
                        val servings = recipeServings.toDoubleOrNull()
                        if (servings == null || !servings.isFinite() || servings !in 0.01..1000.0) message = "Enter a valid serving count."
                        else {
                            val recipe = recipes.find { it.id == recipeId }
                            val needed = mealPlan.map { FoodPlanning.Ingredient(it.foodName, 1.0) } + recipe?.ingredients.orEmpty().map { FoodPlanning.Ingredient(it.name, it.servings * servings / requireNotNull(recipe).servings) }
                            val available = rows("pantry").filter { !LocalDate.parse(it.getString("expiry")).isBefore(LocalDate.now()) }.map { FoodPlanning.Ingredient(it.getString("name"), it.getDouble("amount")) }
                            store.update { it.put("grocery", JSONArray(FoodPlanning.groceryList(needed, available).map { row -> JSONObject().put("id", row.name).put("name", row.name).put("amount", row.servings).put("checked", false) })) }
                        }
                    }) { Text("Generate grocery list") }
                    rows("grocery").forEach { row -> Row { Checkbox(row.optBoolean("checked"), { store.putItem("grocery", JSONObject(row.toString()).put("checked", it)) }); Text("${row.getString("name")} • ${"%.2f".format(row.getDouble("amount"))} servings", Modifier.padding(top = 12.dp)) } }
                } }
                item { ToolCard("Dietary and allergy filters") {
                    val diet = data.optString("diet", "Any")
                    Text("Only foods with confirmed ingredient metadata are included when filters are active. Always check the product label.")
                    ChoiceRow(listOf("Any", "Vegetarian", "Vegan", "Halal"), diet) { store.update { root -> root.put("diet", it) } }
                    Text("Exclude allergens")
                    val excluded = data.optString("excluded").split(",").filter(String::isNotBlank).toSet()
                    MultiChoices(listOf("Milk", "Egg", "Peanut", "Tree nut", "Wheat", "Soy", "Fish", "Shellfish", "Sesame"), excluded) { store.update { root -> root.put("excluded", it.joinToString(",")) } }
                    Text("Choose a saved food to confirm its metadata")
                    ChoiceRow(foods.map { it.id }, selectedFood.orEmpty(), { selectedFood = it }, { id -> foods.find { it.id == id }?.name ?: id })
                    val food = foods.find { it.id == selectedFood }
                    if (food != null) {
                        val metadata = rows("foodMetadata").find { it.getString("id") == food.id } ?: JSONObject().put("id", food.id)
                        Text("Dietary certifications you have checked")
                        MultiChoices(listOf("Vegetarian", "Vegan", "Halal"), metadata.optString("tags").split(",").filter(String::isNotBlank).toSet()) { store.putItem("foodMetadata", JSONObject(metadata.toString()).put("tags", it.joinToString(","))) }
                        Text("Contains or may contain")
                        MultiChoices(listOf("Milk", "Egg", "Peanut", "Tree nut", "Wheat", "Soy", "Fish", "Shellfish", "Sesame"), metadata.optString("allergens").split(",").filter(String::isNotBlank).toSet()) { store.putItem("foodMetadata", JSONObject(metadata.toString()).put("allergens", it.joinToString(","))) }
                        ToolSwitch("I checked the ingredient label", metadata.optBoolean("verified")) { store.putItem("foodMetadata", JSONObject(metadata.toString()).put("verified", it)) }
                    }
                    Text("Matching foods", fontWeight = FontWeight.Bold)
                    val matches = foods.filter { f -> val m = rows("foodMetadata").find { it.getString("id") == f.id }; FoodPlanning.allowed(m?.optString("tags").orEmpty().split(",").toSet(), m?.optString("allergens").orEmpty().split(",").toSet(), diet, excluded, m?.optBoolean("verified") == true) }
                    if (matches.isEmpty()) Text("No confirmed matches.")
                    matches.take(10).forEach { Text(it.name) }
                } }
                item { ToolCard("Additional nutrients") {
                    Text("Attach label values to a diary entry. Unrecorded values remain unknown, not zero.")
                    val today = entries.filter { java.time.Instant.ofEpochMilli(it.consumedAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
                    ChoiceRow(today.map { it.id }, selectedEntry.orEmpty(), { selectedEntry = it }, { id -> today.find { it.id == id }?.name ?: id })
                    ChoiceRow(listOf("Fibre (g)", "Sugar (g)", "Sodium (mg)", "Potassium (mg)", "Calcium (mg)", "Iron (mg)", "Vitamin A (µg)", "Vitamin C (mg)", "Vitamin D (µg)", "Vitamin B12 (µg)"), nutrient) { nutrient = it }
                    OutlinedTextField(nutrientAmount, { nutrientAmount = it }, label = { Text("Amount for this diary entry") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Button(onClick = { val amount = nutrientAmount.toDoubleOrNull(); if (amount != null && amount.isFinite() && amount in 0.0..100000.0 && today.any { it.id == selectedEntry }) {
                        store.putItem("nutrients", JSONObject().put("id", "$selectedEntry:$nutrient").put("entry", selectedEntry).put("nutrient", nutrient).put("amount", amount)); nutrientAmount = ""
                    } else message = "Select an entry and enter a valid amount." }) { Text("Save nutrient value") }
                    val logged = rows("nutrients").filter { value -> today.any { it.id == value.optString("entry") } }
                    logged.groupBy { it.getString("nutrient") }.forEach { (label, values) -> Text("$label: ${"%.2f".format(values.sumOf { it.getDouble("amount") })} • ${values.size}/${today.size} entries recorded") }
                } }
            }
            "Training" -> {
                item { ToolCard("Recording preferences") {
                    ToolSwitch("Auto-pause at stops (next workout)", data.optBoolean("autoPause")) { store.update { root -> root.put("autoPause", it) } }
                    ToolSwitch("Battery-aware GPS (next workout)", data.optBoolean("adaptiveGps")) { store.update { root -> root.put("adaptiveGps", it) } }
                    Text("Auto-pause requires 10 seconds of reliable low-speed fixes. Manual pauses stay paused. Battery mode samples every 1–5 seconds and may reduce route detail.")
                } }
                item { ToolCard("Training load") { Text(TrainingAnalysis.load(workouts, System.currentTimeMillis())) } }
                item { Button(onClick = { importGpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Import GPX route") } }
                items(workouts, key = { "workout_" + it.id }) { workout -> ToolCard("${workout.type} • ${"%.2f".format(workout.distanceMeters / 1000)} km") {
                    ElevationProfile(workout.route, workout.distanceMeters, workout.durationSeconds)
                    val laps = rows("laps").find { it.getString("id") == workout.id }?.optJSONArray("items")
                    if (laps != null) (0 until laps.length()).forEach { i -> val lap = laps.getJSONObject(i); Text("Lap ${i + 1}: ${lap.getDouble("meters").toInt()} m / ${lap.getLong("seconds")} s cumulative") }
                    OutlinedButton(onClick = { exportRoute = workout.route; confirmation = "gpx" }, enabled = workout.route.size >= 2 && !busy) { Text("Export GPX") }
                } }
                item { Button(onClick = { task {
                    val intent = withContext(Dispatchers.IO) { CoachReport.create(context, profile.name, workouts, entries, weights) }
                    context.startActivity(Intent.createChooser(intent, "Share progress report")); "PDF report prepared."
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Share 28-day PDF report") } }
            }
            "Privacy" -> {
                item { ToolCard("Cross-device cloud sync") {
                    Text("Sync saves a complete copy of workouts, diary, recipes, plans, goals and preferences. Conflicting copies require your choice. Food-photo files remain on this phone. Maximum snapshot size: 10 MB.")
                    OutlinedButton(onClick = { confirmation = "claim" }, enabled = !busy) { Text("Confirm ownership of this device's data") }
                    ToolSwitch("Include detailed routes in cloud snapshot", data.optBoolean("syncRoutes")) { enabled -> if (enabled) confirmation = "routes" else store.update { it.put("syncRoutes", false) } }
                    ToolSwitch("Round cloud route coordinates (~100 m)", data.optBoolean("approximateCloudRoutes")) { enabled -> store.update { it.put("approximateCloudRoutes", enabled) } }
                    Button(onClick = { runSync() }, enabled = !busy) { Text("Sync now") }
                    OutlinedButton(onClick = { confirmation = "download" }, enabled = !busy) { Text("Restore cloud copy") }
                    TextButton(onClick = { confirmation = "deleteCloud" }, enabled = !busy) { Text("Delete cloud snapshot") }
                } }
                item { ToolCard("Privacy controls") {
                    Text("Local retention deletes older diary, weight, water and workout records. Cloud snapshots are separate; sync after deletion to update them.")
                    ChoiceRow(listOf("Keep all", "30 days", "90 days", "365 days"), data.optString("retention", "Keep all")) { value -> store.update { it.put("retention", value) } }
                    OutlinedButton(onClick = { confirmation = "retention" }, enabled = !busy && data.optString("retention", "Keep all") != "Keep all") { Text("Apply retention now") }
                    ToolSwitch("Send crash and performance diagnostics", data.optBoolean("diagnostics")) { enabled ->
                        store.update { it.put("diagnostics", enabled) }
                        runCatching {
                            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
                            com.google.firebase.perf.FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled
                            if (!enabled) com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().deleteUnsentReports()
                        }.onFailure { message = "Firebase diagnostics is not configured for this build." }
                    }
                    Text("Exports and account deletion remain available from Profile. Diagnostic collection is off by default.")
                } }
            }
            "Feedback" -> item { ToolCard("Report a problem") {
                OutlinedTextField(feedback, { feedback = it.take(4000) }, label = { Text("What happened? What did you expect?") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                ToolSwitch("Attach app and Android versions", diagnosticAttachment) { diagnosticAttachment = it }
                val attachment = "MotionFuel ${BuildConfig.VERSION_NAME}; Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}"
                if (diagnosticAttachment) Text(attachment)
                Button(onClick = { runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "MotionFuel feedback").putExtra(Intent.EXTRA_TEXT, feedback + if (diagnosticAttachment) "\n\n$attachment" else ""), "Send feedback"))
                }.onFailure { message = "No sharing app is available." } }, enabled = feedback.isNotBlank()) { Text("Share feedback") }
                Text("You choose the recipient in the share sheet.")
            } }
        }
    }
    if (conflict) AlertDialog(onDismissRequest = { conflict = false }, title = { Text("Resolve sync conflict") }, text = { Text("Keeping this device replaces the cloud snapshot. Keeping the cloud copy replaces this device's records. Export important records first.") }, confirmButton = { TextButton(onClick = { conflict = false; runSync("upload") }) { Text("Keep this device") } }, dismissButton = { Column { TextButton(onClick = { conflict = false; runSync("download") }) { Text("Keep cloud copy") }; TextButton(onClick = { conflict = false }) { Text("Cancel") } } })
    confirmation?.let { action ->
        val description = when (action) {
            "claim" -> "Confirm that the records currently on this phone belong to ${profile.name}."
            "routes" -> "Detailed routes can reveal home and other sensitive locations. Include them in your private cloud snapshot?"
            "gpx" -> "The exported file contains the complete route, timestamps and endpoints. Continue?"
            "download" -> "Replace this device's records with the cloud copy? Local changes since your last sync will be lost."
            "deleteCloud" -> "Delete your current cloud snapshot? Local records will remain."
            else -> "Delete local records older than ${data.optString("retention")}? This cannot be undone."
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Confirm") }, text = { Text(description) }, confirmButton = { TextButton(onClick = {
            confirmation = null
            when (action) {
                "claim" -> task { sync.claimLocalData(); "Device ownership confirmed." }
                "routes" -> store.update { it.put("syncRoutes", true) }
                "gpx" -> saveGpx.launch("motionfuel-route.gpx")
                "download" -> runSync("download")
                "deleteCloud" -> task { sync.deleteCloudSnapshot() }
                "retention" -> task {
                    val days = data.getString("retention").substringBefore(' ').toLong()
                    val cutoff = System.currentTimeMillis() - days * 86_400_000L
                    withContext(Dispatchers.IO) {
                        val db = app.database.openHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            mapOf("workouts" to "startedAtMillis", "nutrition_entries" to "consumedAtMillis", "weight_entries" to "recordedAtMillis", "hydration_entries" to "consumedAtMillis").forEach { (table, column) -> db.execSQL("DELETE FROM `$table` WHERE `$column` < ?", arrayOf(cutoff)) }
                            db.setTransactionSuccessful()
                        } finally { db.endTransaction() }
                        app.database.invalidationTracker.refreshAsync()
                    }
                    "Retention applied. Sync again to update the cloud copy."
                }
            }
        }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}

@Composable private fun ToolCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content() } }
}
@Composable private fun ToolSwitch(label: String, checked: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, changed) }
}
@Composable private fun ChoiceRow(values: List<String>, selected: String, changed: (String) -> Unit, label: (String) -> String) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { value -> FilterChip(selected == value, { changed(value) }, { Text(label(value)) }) } }
}
@Composable private fun ChoiceRow(values: List<String>, selected: String, changed: (String) -> Unit) = ChoiceRow(values, selected, changed, { it })
@Composable private fun MultiChoices(values: List<String>, selected: Set<String>, changed: (Set<String>) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { value -> FilterChip(value in selected, { changed(if (value in selected) selected - value else selected + value) }, { Text(value) }) } }
}
@Composable fun ElevationProfile(points: List<GeoPoint>, distanceMeters: Double, durationSeconds: Long) {
    val result = remember(points) { TrainingAnalysis.elevation(points) }
    if (result.samples.size < 2) { Text("Elevation unavailable for this route."); return }
    Text("Ascent ${result.ascent.toInt()} m • Descent ${result.descent.toInt()} m")
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val low = result.samples.min(); val range = (result.samples.max() - low).coerceAtLeast(1.0)
        result.samples.zipWithNext().forEachIndexed { i, pair ->
            fun point(index: Int, value: Double) = Offset(index.toFloat() / (result.samples.size - 1) * size.width, size.height - ((value - low) / range * size.height).toFloat())
            drawLine(color, point(i, pair.first), point(i + 1, pair.second), 3f)
        }
    }
    if (distanceMeters > 0) {
        val effortDistance = distanceMeters + result.ascent * 10
        Text("Hill-effort distance: ${"%.2f".format(effortDistance / 1000)} km\nHeuristic: distance + 10 × ascent; not a measured flat-equivalent pace.")
        if (durationSeconds > 0) Text("Effort-adjusted pace estimate: ${"%.1f".format(durationSeconds / 60.0 / (effortDistance / 1000))} min/km")
    }
}
