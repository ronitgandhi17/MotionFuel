package com.ronitgandhi.motionfuel.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.model.FoodSearchResult
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.SavedFood
import com.ronitgandhi.motionfuel.ui.components.MacroProgress
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelSky
import coil.compose.AsyncImage

@Composable
fun FoodScreen(
    totals: NutritionTotals,
    entries: List<NutritionEntry>,
    savedFoods: List<SavedFood>,
    results: List<FoodSearchResult>,
    searchStatus: String?,
    onSearch: (String) -> Unit,
    onAddFood: (FoodSearchResult, MealType) -> Unit,
    onAddManual: (String, Double, Double, Double, Double, MealType, String?) -> Unit,
    onAddSavedFood: (SavedFood, MealType) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var manualDialog by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf(MealType.BREAKFAST) }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedPhotoUri = it.toString()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Diary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Today", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Energy today", style = MaterialTheme.typography.labelLarge)
                            Text("${totals.caloriesKcal.toInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                        }
                        Text("of 2,200 kcal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MacroProgress("Protein", totals.proteinG, 100.0, FuelSky)
                    MacroProgress("Carbohydrate", totals.carbohydratesG, 260.0, FuelOrange)
                    MacroProgress("Fat", totals.fatG, 75.0, FuelGreen)
                }
            }
        }
        MealType.entries.forEach { meal ->
            item(key = "meal-${meal.name}") {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(meal.name.lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Bold)
                            Text("${entries.filter { it.mealType == meal }.sumOf { it.caloriesKcal }.toInt()} kcal", fontWeight = FontWeight.Bold)
                        }
                        entries.filter { it.mealType == meal }.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                Text(entry.caloriesKcal.toInt().toString(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        TextButton(onClick = { selectedMeal = meal }) { Icon(Icons.Rounded.Add, null); Text("Add food") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search food") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onSearch(query) }, modifier = Modifier.height(56.dp)) { Text("Find") }
            }
            searchStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            Text("Add to", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MealType.entries.forEach { meal ->
                    FilterChip(selected = selectedMeal == meal, onClick = { selectedMeal = meal }, label = { Text(meal.name.take(1) + meal.name.drop(1).lowercase()) })
                }
            }
        }
        item {
            Text("My saved foods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Manual foods are saved here for quick reuse.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (savedFoods.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    Text("Add a manual food to build your saved list.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(savedFoods, key = { "saved-${it.id}" }) { food ->
                SavedFoodCard(food = food, onAdd = { onAddSavedFood(food, selectedMeal) })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Search results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { manualDialog = true }) { Icon(Icons.Rounded.Add, null); Text("Manual") }
            }
        }
        items(results, key = { it.providerId }) { food ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = FuelGreen.copy(alpha = 0.15f)) {
                        Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = FuelGreen, modifier = Modifier.padding(9.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(food.name, fontWeight = FontWeight.Bold)
                        Text("${food.servingLabel} • ${food.caloriesKcal.toInt()} kcal • ${food.proteinG.toInt()} g protein", style = MaterialTheme.typography.bodySmall)
                        food.brand?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = { onAddFood(food, selectedMeal) }) { Icon(Icons.Rounded.Add, contentDescription = "Add ${food.name}") }
                }
            }
        }
    }
    if (manualDialog) {
        ManualFoodDialog(
            initialMeal = selectedMeal,
            photoUri = selectedPhotoUri,
            onPickPhoto = { photoPicker.launch(arrayOf("image/*")) },
            onDismiss = {
                selectedPhotoUri = null
                manualDialog = false
            },
        ) { name, calories, protein, carbs, fat, meal, photoUri ->
            onAddManual(name, calories, protein, carbs, fat, meal, photoUri)
            selectedPhotoUri = null
            manualDialog = false
        }
    }
}

@Composable
private fun SavedFoodCard(food: SavedFood, onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (food.photoUri != null) {
                AsyncImage(
                    model = food.photoUri,
                    contentDescription = "Photo of ${food.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Surface(shape = RoundedCornerShape(14.dp), color = FuelGreen.copy(alpha = 0.15f), modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = FuelGreen, modifier = Modifier.padding(18.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.Bold)
                Text("${food.caloriesKcal.toInt()} kcal • ${food.proteinG.toInt()} g protein", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Add ${food.name} to diary") }
        }
    }
}

@Composable
private fun ManualFoodDialog(
    initialMeal: MealType,
    photoUri: String?,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, MealType, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(initialMeal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual food entry") },
        text = {
            Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (photoUri == null) "Add food picture" else "Change food picture")
                }
                photoUri?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Selected food picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)),
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text("Food name") }, singleLine = true)
                OutlinedTextField(calories, { calories = it }, label = { Text("Calories") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(protein, { protein = it }, label = { Text("Protein") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(carbs, { carbs = it }, label = { Text("Carbs") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(fat, { fat = it }, label = { Text("Fat") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.entries.forEach { option -> FilterChip(selected = meal == option, onClick = { meal = option }, label = { Text(option.name.take(1) + option.name.drop(1).lowercase()) }) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && calories.toDoubleOrNull() != null,
                onClick = { onSave(name, calories.toDoubleOrNull() ?: 0.0, protein.toDoubleOrNull() ?: 0.0, carbs.toDoubleOrNull() ?: 0.0, fat.toDoubleOrNull() ?: 0.0, meal, photoUri) },
            ) { Text("Save locally") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
