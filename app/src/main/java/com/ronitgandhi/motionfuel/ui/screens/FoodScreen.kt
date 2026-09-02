package com.ronitgandhi.motionfuel.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import coil.compose.AsyncImage
import com.ronitgandhi.motionfuel.domain.algorithm.NutritionMath
import com.ronitgandhi.motionfuel.domain.model.CustomMeal
import com.ronitgandhi.motionfuel.domain.model.FoodSearchResult
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import com.ronitgandhi.motionfuel.ui.components.MacroProgress
import com.ronitgandhi.motionfuel.ui.components.SectionHeader
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelRose
import com.ronitgandhi.motionfuel.ui.theme.FuelSky
import java.util.Locale
import kotlin.math.roundToInt

// Ordered meal slots rendered as their own logged-food cards.
private val foodMealOrder = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)

// Display label for a meal slot.
private fun foodMealLabel(meal: MealType): String = when (meal) {
    MealType.BREAKFAST -> "Breakfast"
    MealType.LUNCH -> "Lunch"
    MealType.DINNER -> "Dinner"
    MealType.SNACK -> "Snack"
}

private fun foodFormat(value: Double): String = String.format(Locale.US, "%.0f", value)

@Composable
fun FoodScreen(
    profile: UserProfile?,
    totals: NutritionTotals,
    entries: List<NutritionEntry>,
    customMeals: List<CustomMeal>,
    results: List<FoodSearchResult>,
    searchStatus: String?,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onAddFood: (FoodSearchResult, MealType, Double) -> Unit,
    onAddManual: (String, Double, Double, Double, Double, MealType) -> Unit,
    onLogCustomMeal: (CustomMeal, MealType, Double) -> Unit,
    onSaveCustomMeal: (String, Double, Double, Double, Double, String?) -> Unit,
    onDeleteCustomMeal: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    LazyColumnScreen {
        item { FoodDailyHeader(profile, totals) }
        item { FoodSearchSection(results, searchStatus, onSearch, onClearSearch, onAddFood) }
        foodMealOrder.forEach { meal ->
            item { FoodMealCard(meal, entries, profile, onDeleteEntry) }
        }
        item { FoodManualAddForm(onAddManual) }
        item { FoodCustomMealsSection(customMeals, onLogCustomMeal, onSaveCustomMeal, onDeleteCustomMeal) }
    }
}
@Composable
private fun LazyColumnScreen(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun FoodDailyHeader(profile: UserProfile?, totals: NutritionTotals) {
    val goal = profile?.dailyCalorieGoal?.takeIf { it > 0 } ?: 2200
    val percent = NutritionMath.percentOfGoal(totals.caloriesKcal, goal)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Food", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Energy today", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${foodFormat(totals.caloriesKcal)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text("of $goal kcal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = FuelGreen,
                    trackColor = FuelGreen.copy(alpha = 0.15f),
                )
                Text("$percent% of goal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MacroProgress("Protein", totals.proteinG, profile?.proteinTargetG ?: 120.0, FuelSky)
                MacroProgress("Carbohydrate", totals.carbohydratesG, 260.0, FuelOrange)
                MacroProgress("Fat", totals.fatG, 75.0, FuelRose)
            }
        }
    }
}
@Composable
private fun FoodSearchSection(
    results: List<FoodSearchResult>,
    searchStatus: String?,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onAddFood: (FoodSearchResult, MealType, Double) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pending: FoodSearchResult? by remember { mutableStateOf(null) }
    // Debounce: wait 350 ms after the last keystroke before hitting the network.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            onClearSearch()
        } else {
            kotlinx.coroutines.delay(350)
            onSearch(query)
        }
    }
    val isLoading = searchStatus?.contains("Searching", ignoreCase = true) == true
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Search foods")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search Open Food Facts") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(searchStatus ?: "Searching…", style = MaterialTheme.typography.labelMedium)
            }
            searchStatus != null && results.isEmpty() -> Text(
                searchStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            query.isNotBlank() && results.isEmpty() -> Text(
                "No matches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        results.forEach { food ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, fontWeight = FontWeight.Bold)
                        food.brand?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(
                            "${food.servingLabel} • ${foodFormat(food.caloriesKcal)} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { pending = food }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Add")
                    }
                }
            }
        }
    }
    pending?.let { food ->
        FoodLogDialog(
            title = "Add ${food.name}",
            onDismiss = { pending = null },
            onConfirm = { meal, qty ->
                onAddFood(food, meal, qty)
                pending = null
            },
        )
    }
}
@Composable
private fun FoodMealCard(
    meal: MealType,
    entries: List<NutritionEntry>,
    profile: UserProfile?,
    onDeleteEntry: (String) -> Unit,
) {
    val mealEntries = entries.filter { it.mealType == meal }
    val mealCalories = NutritionMath.caloriesForMeal(entries, meal)
    val goal = profile?.dailyCalorieGoal?.takeIf { it > 0 } ?: 2200
    val percent = NutritionMath.percentOfGoal(mealCalories, goal)
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(foodMealLabel(meal), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${foodFormat(mealCalories)} kcal • $percent%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mealEntries.isEmpty()) {
                Text("Nothing logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                mealEntries.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                            val qtyLabel = if (entry.quantity != 1.0) " ×${foodFormat(entry.quantity)}" else ""
                            Text(
                                "${entry.servingLabel}$qtyLabel • ${foodFormat(entry.caloriesKcal)} kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteEntry(entry.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${entry.name}")
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun FoodMealChips(selected: MealType, onSelect: (MealType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        foodMealOrder.forEach { meal ->
            FilterChip(
                selected = selected == meal,
                onClick = { onSelect(meal) },
                label = { Text(foodMealLabel(meal)) },
            )
        }
    }
}

@Composable
private fun FoodLogDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (MealType, Double) -> Unit,
) {
    var meal by remember { mutableStateOf(MealType.SNACK) }
    var qty by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Meal", style = MaterialTheme.typography.labelMedium)
                FoodMealChips(meal) { meal = it }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Servings") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(meal, qty.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
@Composable
private fun FoodManualAddForm(onAddManual: (String, Double, Double, Double, Double, MealType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(MealType.SNACK) }
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick manual entry", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Add") }
            }
            if (expanded) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                foodDecimalField("Calories (kcal)", calories) { calories = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { foodDecimalField("Protein", protein) { protein = it } }
                    Box(Modifier.weight(1f)) { foodDecimalField("Carbs", carbs) { carbs = it } }
                    Box(Modifier.weight(1f)) { foodDecimalField("Fat", fat) { fat = it } }
                }
                FoodMealChips(meal) { meal = it }
                Button(
                    onClick = {
                        val p = protein.toDoubleOrNull() ?: 0.0
                        val c = carbs.toDoubleOrNull() ?: 0.0
                        val f = fat.toDoubleOrNull() ?: 0.0
                        val kcal = calories.toDoubleOrNull() ?: NutritionMath.caloriesFromMacros(p, c, f)
                        if (name.isNotBlank()) {
                            onAddManual(name.trim(), kcal, p, c, f, meal)
                            name = ""; calories = ""; protein = ""; carbs = ""; fat = ""
                            expanded = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Log entry") }
            }
        }
    }
}

@Composable
private fun foodDecimalField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
@Composable
private fun FoodCustomMealsSection(
    customMeals: List<CustomMeal>,
    onLogCustomMeal: (CustomMeal, MealType, Double) -> Unit,
    onSaveCustomMeal: (String, Double, Double, Double, Double, String?) -> Unit,
    onDeleteCustomMeal: (String) -> Unit,
) {
    var logging: CustomMeal? by remember { mutableStateOf(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Custom meals")
        customMeals.forEach { meal ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    meal.photoUri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp).padding(end = 12.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(meal.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${foodFormat(meal.caloriesKcal)} kcal • P${foodFormat(meal.proteinG)} C${foodFormat(meal.carbohydratesG)} F${foodFormat(meal.fatG)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { logging = meal }) { Text("Log") }
                    IconButton(onClick = { onDeleteCustomMeal(meal.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete ${meal.name}")
                    }
                }
            }
        }
        FoodCustomMealForm(onSaveCustomMeal)
    }
    logging?.let { meal ->
        FoodLogDialog(
            title = "Log ${meal.name}",
            onDismiss = { logging = null },
            onConfirm = { slot, qty ->
                onLogCustomMeal(meal, slot, qty)
                logging = null
            },
        )
    }
}
@Composable
private fun FoodCustomMealForm(onSaveCustomMeal: (String, Double, Double, Double, Double, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var caloriesEdited by remember { mutableStateOf(false) }
    var photoUri: String? by remember { mutableStateOf(null) }

    val p = protein.toDoubleOrNull() ?: 0.0
    val c = carbs.toDoubleOrNull() ?: 0.0
    val f = fat.toDoubleOrNull() ?: 0.0
    val estimate = NutritionMath.caloriesFromMacros(p, c, f)
    // Auto-prefill calories from 4/4/9 until the user types their own value.
    val caloriesShown = if (caloriesEdited) calories else if (estimate > 0) foodFormat(estimate) else ""

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        photoUri = uri?.toString()
    }

    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create custom meal", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { foodDecimalField("Protein", protein) { protein = it } }
                Box(Modifier.weight(1f)) { foodDecimalField("Carbs", carbs) { carbs = it } }
                Box(Modifier.weight(1f)) { foodDecimalField("Fat", fat) { fat = it } }
            }
            OutlinedTextField(
                value = caloriesShown,
                onValueChange = { calories = it; caloriesEdited = true },
                label = { Text("Calories (kcal) — 4/4/9 estimate") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Pick photo")
                }
                photoUri?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(48.dp)) }
            }
            Button(
                onClick = {
                    val kcal = caloriesShown.toDoubleOrNull() ?: estimate
                    if (name.isNotBlank()) {
                        onSaveCustomMeal(name.trim(), kcal, p, c, f, photoUri)
                        name = ""; protein = ""; carbs = ""; fat = ""; calories = ""; caloriesEdited = false; photoUri = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Save meal") }
        }
    }
}



