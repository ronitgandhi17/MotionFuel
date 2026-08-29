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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ronitgandhi.motionfuel.domain.model.FoodSearchResult
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.ui.components.MacroProgress
import com.ronitgandhi.motionfuel.ui.theme.FuelGreen
import com.ronitgandhi.motionfuel.ui.theme.FuelOrange
import com.ronitgandhi.motionfuel.ui.theme.FuelSky

@Composable
fun FoodScreen(
    totals: NutritionTotals,
    entries: List<NutritionEntry>,
    results: List<FoodSearchResult>,
    searchStatus: String?,
    onSearch: (String) -> Unit,
    onAddFood: (FoodSearchResult) -> Unit,
    onAddManual: (String, Double, Double, Double, Double) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var manualDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Fuel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Nutrition that updates the same daily context as your activity.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(25.dp)) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Search results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { manualDialog = true }) { Icon(Icons.Rounded.Add, null); Text("Manual") }
            }
        }
        if (results.isEmpty()) {
            item {
                Text(
                    "Search for a food above or add one manually. Results come from Open Food Facts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    IconButton(onClick = { onAddFood(food) }) { Icon(Icons.Rounded.Add, contentDescription = "Add ${food.name}") }
                }
            }
        }
        item {
            Text("Logged today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (entries.isEmpty()) {
            item { Text("Nothing logged yet. Food entries save locally before any sync.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(entries, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold)
                        Text(entry.mealType.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${entry.caloriesKcal.toInt()} kcal", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (manualDialog) {
        ManualFoodDialog(onDismiss = { manualDialog = false }) { name, calories, protein, carbs, fat ->
            onAddManual(name, calories, protein, carbs, fat)
            manualDialog = false
        }
    }
}

@Composable
private fun ManualFoodDialog(onDismiss: () -> Unit, onSave: (String, Double, Double, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual food entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Food name") }, singleLine = true)
                OutlinedTextField(calories, { calories = it }, label = { Text("Calories") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(protein, { protein = it }, label = { Text("Protein") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(carbs, { carbs = it }, label = { Text("Carbs") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(fat, { fat = it }, label = { Text("Fat") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && calories.toDoubleOrNull() != null,
                onClick = { onSave(name, calories.toDoubleOrNull() ?: 0.0, protein.toDoubleOrNull() ?: 0.0, carbs.toDoubleOrNull() ?: 0.0, fat.toDoubleOrNull() ?: 0.0) },
            ) { Text("Save locally") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
