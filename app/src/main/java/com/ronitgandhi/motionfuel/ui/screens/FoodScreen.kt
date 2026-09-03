package com.ronitgandhi.motionfuel.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import java.io.File

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
    onDeleteSavedFood: (SavedFood) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var manualDialog by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf(MealType.BREAKFAST) }
    var selectedSavedFood by remember { mutableStateOf<SavedFood?>(null) }
    var pendingAddFood by remember { mutableStateOf<SavedFood?>(null) }
    var pendingDeleteFood by remember { mutableStateOf<SavedFood?>(null) }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
    var selectedPhotoFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (!captured) {
            selectedPhotoFile?.delete()
            selectedPhotoFile = null
            selectedPhotoUri = null
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedPhotoFile?.delete()
            selectedPhotoFile = null
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedPhotoUri = it.toString()
        }
    }

    selectedSavedFood?.let { food ->
        SavedFoodDetailScreen(
            food = food,
            onBack = { selectedSavedFood = null },
            onAdd = { meal ->
                selectedMeal = meal
                onAddSavedFood(food, meal)
            },
            onShare = { shareSavedFood(context, food) },
        )
        return
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
                SavedFoodCard(
                    food = food,
                    onOpen = { selectedSavedFood = food },
                    onAdd = { onAddSavedFood(food, selectedMeal) },
                    onRequestAdd = { pendingAddFood = food },
                    onRequestDelete = { pendingDeleteFood = food },
                )
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
            onTakePhoto = {
                selectedPhotoFile?.delete()
                val directory = File(context.filesDir, "food_photos").apply { mkdirs() }
                val photoFile = File.createTempFile("food_", ".jpg", directory)
                val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                selectedPhotoFile = photoFile
                selectedPhotoUri = photoUri.toString()
                cameraLauncher.launch(photoUri)
            },
            onChooseGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = {
                selectedPhotoFile?.delete()
                selectedPhotoFile = null
                selectedPhotoUri = null
                manualDialog = false
            },
        ) { name, calories, protein, carbs, fat, meal, photoUri ->
            onAddManual(name, calories, protein, carbs, fat, meal, photoUri)
            selectedPhotoFile = null
            selectedPhotoUri = null
            manualDialog = false
        }
    }
    pendingAddFood?.let { food ->
        AddSavedFoodToDayDialog(
            food = food,
            initialMeal = selectedMeal,
            onDismiss = { pendingAddFood = null },
        ) { meal ->
            selectedMeal = meal
            onAddSavedFood(food, meal)
            pendingAddFood = null
        }
    }
    pendingDeleteFood?.let { food ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFood = null },
            title = { Text("Delete saved food?") },
            text = { Text("${food.name} will be removed from My saved foods. Existing diary entries will remain.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSavedFood(food)
                    pendingDeleteFood = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteFood = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedFoodCard(food: SavedFood, onOpen: () -> Unit, onAdd: () -> Unit, onRequestAdd: () -> Unit, onRequestDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onRequestAdd()
                SwipeToDismissBoxValue.EndToStart -> onRequestDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = FuelGreen)
                    Text("Add to day", style = MaterialTheme.typography.labelSmall, color = FuelGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun AddSavedFoodToDayDialog(food: SavedFood, initialMeal: MealType, onDismiss: () -> Unit, onAdd: (MealType) -> Unit) {
    var meal by remember(food.id) { mutableStateOf(initialMeal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${food.name} to today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a meal for this diary entry.")
                MealType.entries.forEach { option ->
                    FilterChip(
                        selected = meal == option,
                        onClick = { meal = option },
                        label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(meal) }) { Text("Add to today") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SavedFoodDetailScreen(food: SavedFood, onBack: () -> Unit, onAdd: (MealType) -> Unit, onShare: () -> Unit) {
    var showMealSelector by remember(food.id) { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to saved foods") }
                Text("Saved food", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
        }
        food.photoUri?.let { photoUri ->
            item {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Photo of ${food.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(22.dp)),
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(food.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Per saved serving", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FoodNutrientRow("Calories", "${food.caloriesKcal.toInt()} kcal")
                    FoodNutrientRow("Protein", "${food.proteinG.toInt()} g")
                    FoodNutrientRow("Carbohydrates", "${food.carbohydratesG.toInt()} g")
                    FoodNutrientRow("Fat", "${food.fatG.toInt()} g")
                }
            }
        }
        item {
            Button(onClick = { showMealSelector = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Add to meal")
            }
        }
        item {
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Share food")
            }
        }
    }
    if (showMealSelector) {
        SelectDetailMealDialog(
            food = food,
            onDismiss = { showMealSelector = false },
        ) { meal ->
            onAdd(meal)
            showMealSelector = false
        }
    }
}

@Composable
private fun SelectDetailMealDialog(food: SavedFood, onDismiss: () -> Unit, onAdd: (MealType) -> Unit) {
    var meal by remember(food.id) { mutableStateOf(MealType.BREAKFAST) }
    val mealOptions = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${food.name} to meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose where to add this food in today's diary.")
                mealOptions.forEach { option ->
                    FilterChip(
                        selected = meal == option,
                        onClick = { meal = option },
                        label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(meal) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FoodNutrientRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun shareSavedFood(context: android.content.Context, food: SavedFood) {
    val summary = "${food.name} • ${food.caloriesKcal.toInt()} kcal • ${food.proteinG.toInt()} g protein • ${food.carbohydratesG.toInt()} g carbs • ${food.fatG.toInt()} g fat"
    val photoUri = food.photoUri?.let(Uri::parse)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = if (photoUri == null) "text/plain" else "image/jpeg"
        putExtra(Intent.EXTRA_TEXT, summary)
        photoUri?.let {
            putExtra(Intent.EXTRA_STREAM, it)
            clipData = ClipData.newRawUri(food.name, it)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share ${food.name}"))
}

@Composable
private fun ManualFoodDialog(
    initialMeal: MealType,
    photoUri: String?,
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
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
                Text(if (photoUri == null) "Add food picture" else "Change food picture", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Camera")
                    }
                    OutlinedButton(onClick = onChooseGallery, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Gallery")
                    }
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
