package com.ronitgandhi.motionfuel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ronitgandhi.motionfuel.domain.algorithm.AdaptiveInsightEngine
import com.ronitgandhi.motionfuel.domain.algorithm.FoodPhotoPolicy
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.DailyContext
import com.ronitgandhi.motionfuel.domain.model.FoodSearchResult
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.SavedFood
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.service.DemoTracePlayer
import com.ronitgandhi.motionfuel.service.WorkoutSessionController
import com.ronitgandhi.motionfuel.service.WorkoutTrackingService
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MotionFuelViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MotionFuelApplication
    private val repository = app.repository
    private val settingsRepository = app.settingsRepository
    private val api = app.apiClient
    private val insightEngine = AdaptiveInsightEngine()
    private val demoPlayer = DemoTracePlayer()
    private val todayRange = localDayRange()
    private val thirtyDaysAgo = todayRange.first - (29L * 86_400_000L)
    private var workoutStartedAtMillis = 0L

    val telemetry = WorkoutSessionController.telemetry
    val settings: StateFlow<UserSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )
    val workouts: StateFlow<List<WorkoutSummary>> = repository.observeWorkouts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val nutritionEntries: StateFlow<List<NutritionEntry>> =
        repository.observeNutritionEntries(todayRange.first, todayRange.second).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    val nutritionTotals: StateFlow<NutritionTotals> =
        repository.observeNutritionTotals(todayRange.first, todayRange.second).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            NutritionTotals(),
        )
    val nutritionHistory: StateFlow<List<NutritionEntry>> =
        repository.observeNutritionEntries(thirtyDaysAgo, todayRange.second).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    val weightEntries: StateFlow<List<WeightEntry>> = repository.observeWeightEntries(thirtyDaysAgo).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val savedFoods: StateFlow<List<SavedFood>> = repository.observeSavedFoods().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val mutableWeather = MutableStateFlow(
        WeatherContext(temperatureC = 19.0, humidityPercent = 61, windSpeedKph = 13.0, isRaining = false, sourceAgeMinutes = 0),
    )
    val weather = mutableWeather.asStateFlow()
    private val mutableWeatherStatus = MutableStateFlow("Demo-safe cached context")
    val weatherStatus = mutableWeatherStatus.asStateFlow()

    private val mutableFoodResults = MutableStateFlow(sampleFoods())
    val foodResults = mutableFoodResults.asStateFlow()
    private val mutableFoodSearchStatus = MutableStateFlow<String?>(null)
    val foodSearchStatus = mutableFoodSearchStatus.asStateFlow()

    val insights: StateFlow<List<Insight>> = combine(
        telemetry,
        nutritionTotals,
        mutableWeather,
        workouts,
    ) { workout, nutrition, weather, history ->
        val baselineDuration = history.take(5).map { it.durationSeconds / 60.0 }.average().takeIf { !it.isNaN() } ?: 30.0
        val baselineElevation = history.take(5).map { it.elevationGainMeters }.average().takeIf { !it.isNaN() } ?: 35.0
        val paceDecline = if (workout.currentPaceSecPerKm != null && workout.averagePaceSecPerKm != null) {
            ((workout.currentPaceSecPerKm - workout.averagePaceSecPerKm) / workout.averagePaceSecPerKm * 100).coerceAtLeast(0.0)
        } else 0.0
        val effort = (
            (workout.elapsedSeconds / 2_700.0) * 0.25 +
                (workout.elevationGainMeters / 90.0) * 0.25 +
                (if (weather.temperatureC >= 28) 0.20 else 0.0) +
                (if (workout.activity.type == ActivityType.RUNNING) 0.30 else 0.12)
            ).coerceIn(0.0, 1.0)
        insightEngine.evaluate(
            DailyContext(
                workoutActive = workout.status == WorkoutStatus.ACTIVE,
                paceDeclinePercent = paceDecline,
                effortScore = effort,
                elevationGainMeters = workout.elevationGainMeters,
                elevationBaselineMeters = baselineElevation,
                workoutDurationMinutes = workout.elapsedSeconds / 60.0,
                durationBaselineMinutes = baselineDuration,
                activeMinutesToday = workout.elapsedSeconds / 60.0 + history.filter { it.startedAtMillis >= todayRange.first }.sumOf { it.durationSeconds } / 60.0,
                proteinLoggedG = nutrition.proteinG,
                caloriesLoggedKcal = nutrition.caloriesKcal,
                weather = weather,
                rejectedGpsPoints = workout.rejectedGpsPoints,
                activeDaysThisWeek = distinctActiveDays(history, daysAgo = 0..6),
                activeDaysPreviousWeek = distinctActiveDays(history, daysAgo = 7..13),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshWeather()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            mutableWeatherStatus.value = "Updating Melbourne conditions…"
            api.currentWeather(-37.8136, 144.9631)
                .onSuccess {
                    mutableWeather.value = it
                    mutableWeatherStatus.value = "Live context"
                }
                .onFailure { mutableWeatherStatus.value = "Cached context • offline safe" }
        }
    }

    fun searchFoods(query: String) {
        if (query.isBlank()) {
            mutableFoodResults.value = sampleFoods()
            mutableFoodSearchStatus.value = null
            return
        }
        viewModelScope.launch {
            mutableFoodSearchStatus.value = "Searching Open Food Facts…"
            api.searchFoods(query)
                .onSuccess { results ->
                    mutableFoodResults.value = results.ifEmpty { sampleFoods().filter { it.name.contains(query, ignoreCase = true) } }
                    mutableFoodSearchStatus.value = if (results.isEmpty()) "No online matches • showing local examples" else null
                }
                .onFailure {
                    mutableFoodResults.value = sampleFoods().filter { it.name.contains(query, ignoreCase = true) }
                    mutableFoodSearchStatus.value = "Offline • local examples"
                }
        }
    }

    fun addFood(food: FoodSearchResult, mealType: MealType = MealType.SNACK) {
        viewModelScope.launch {
            repository.saveNutrition(
                NutritionEntry(
                    id = UUID.randomUUID().toString(),
                    name = food.name,
                    caloriesKcal = food.caloriesKcal,
                    proteinG = food.proteinG,
                    carbohydratesG = food.carbohydratesG,
                    fatG = food.fatG,
                    mealType = mealType,
                    consumedAtMillis = System.currentTimeMillis(),
                    createdOffline = mutableFoodSearchStatus.value?.startsWith("Offline") == true,
                ),
            )
        }
    }

    fun addManualFood(name: String, calories: Double, protein: Double, carbs: Double, fat: Double, mealType: MealType, photoUri: String?) {
        val now = System.currentTimeMillis()
        val saved = SavedFood(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Manual food" },
            caloriesKcal = calories.coerceAtLeast(0.0),
            proteinG = protein.coerceAtLeast(0.0),
            carbohydratesG = carbs.coerceAtLeast(0.0),
            fatG = fat.coerceAtLeast(0.0),
            photoUri = FoodPhotoPolicy.sanitize(photoUri),
            createdAtMillis = now,
        )
        viewModelScope.launch {
            repository.saveFood(saved)
            addSavedFoodToDiary(saved, mealType, now)
        }
    }

    fun addSavedFood(food: SavedFood, mealType: MealType) {
        viewModelScope.launch { addSavedFoodToDiary(food, mealType, System.currentTimeMillis()) }
    }

    // Removes the selected diary row and lets Room flows recalculate every displayed total.
    fun deleteNutritionEntry(entry: NutritionEntry) {
        viewModelScope.launch { repository.deleteNutrition(entry.id) }
    }

    fun deleteSavedFood(food: SavedFood) {
        viewModelScope.launch {
            repository.deleteFood(food.id)
            val uri = food.photoUri?.let(android.net.Uri::parse)
            if (uri?.authority == "${getApplication<Application>().packageName}.fileprovider") {
                runCatching { getApplication<Application>().contentResolver.delete(uri, null, null) }
            }
        }
    }

    // Stores a dated weight locally so Progress remains available offline.
    fun addWeight(weightKg: Double) {
        if (weightKg !in 30.0..350.0) return
        viewModelScope.launch {
            repository.saveWeight(WeightEntry(UUID.randomUUID().toString(), weightKg, System.currentTimeMillis()))
            settingsRepository.setWeight(weightKg)
        }
    }

    fun startDemo(type: WorkoutType) {
        workoutStartedAtMillis = System.currentTimeMillis()
        mutableWeather.value = WeatherContext(temperatureC = 30.0, humidityPercent = 48, windSpeedKph = 11.0, isRaining = false)
        mutableWeatherStatus.value = "Assessor demo context • elevated heat"
        demoPlayer.start(viewModelScope, type, settings.value.weightKg)
    }

    fun startReal(type: WorkoutType) {
        workoutStartedAtMillis = System.currentTimeMillis()
        refreshWeather()
        demoPlayer.stop(reset = true)
        val intent = Intent(getApplication(), WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_START
            putExtra(WorkoutTrackingService.EXTRA_TYPE, type.name)
            putExtra(WorkoutTrackingService.EXTRA_WEIGHT_KG, settings.value.weightKg)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun pauseOrResumeWorkout() {
        val current = telemetry.value
        if (current.isDemo) {
            if (current.status == WorkoutStatus.PAUSED) demoPlayer.resume() else demoPlayer.pause()
        } else {
            val action = if (current.status == WorkoutStatus.PAUSED) WorkoutTrackingService.ACTION_RESUME else WorkoutTrackingService.ACTION_PAUSE
            getApplication<Application>().startService(Intent(getApplication(), WorkoutTrackingService::class.java).setAction(action))
        }
    }

    fun finishWorkout() {
        val snapshot = telemetry.value
        if (snapshot.status !in setOf(WorkoutStatus.ACTIVE, WorkoutStatus.PAUSED)) return
        if (snapshot.isDemo) demoPlayer.finish() else {
            getApplication<Application>().startService(
                Intent(getApplication(), WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_STOP),
            )
        }
        viewModelScope.launch {
            repository.saveWorkout(
                WorkoutSummary(
                    id = UUID.randomUUID().toString(),
                    type = snapshot.type,
                    startedAtMillis = workoutStartedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    durationSeconds = snapshot.elapsedSeconds,
                    distanceMeters = snapshot.distanceMeters,
                    averagePaceSecPerKm = snapshot.averagePaceSecPerKm,
                    steps = snapshot.steps,
                    elevationGainMeters = snapshot.elevationGainMeters,
                    caloriesKcal = snapshot.caloriesKcal,
                    dominantActivity = snapshot.activity.type,
                    rejectedGpsPoints = snapshot.rejectedGpsPoints,
                    route = snapshot.route,
                ),
            )
        }
    }

    fun dismissCompletedWorkout() {
        demoPlayer.stop(reset = true)
        WorkoutSessionController.reset()
    }

    fun setUnits(value: UnitSystem) = viewModelScope.launch { settingsRepository.setUnits(value) }
    fun setRouteBackup(value: Boolean) = viewModelScope.launch { settingsRepository.setRouteBackup(value) }
    fun setDarkTheme(value: Boolean) = viewModelScope.launch { settingsRepository.setDarkTheme(value) }
    fun setProfileWeight(value: Double) = viewModelScope.launch { settingsRepository.setWeight(value) }
    fun deleteAllLocalData() = viewModelScope.launch {
        // Releases persisted photo grants when the user deletes all locally stored data.
        savedFoods.value.mapNotNull { it.photoUri }.distinct().forEach { value ->
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(value),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        repository.deleteAllLocalData()
    }

    private suspend fun addSavedFoodToDiary(food: SavedFood, mealType: MealType, consumedAtMillis: Long) {
        repository.saveNutrition(
            NutritionEntry(
                id = UUID.randomUUID().toString(),
                name = food.name,
                caloriesKcal = food.caloriesKcal,
                proteinG = food.proteinG,
                carbohydratesG = food.carbohydratesG,
                fatG = food.fatG,
                mealType = mealType,
                consumedAtMillis = consumedAtMillis,
            ),
        )
    }

    private fun distinctActiveDays(history: List<WorkoutSummary>, daysAgo: IntRange): Int {
        val now = Calendar.getInstance()
        return history.mapNotNull { workout ->
            val day = Calendar.getInstance().apply { timeInMillis = workout.startedAtMillis }
            val diff = ((now.timeInMillis - day.timeInMillis) / 86_400_000L).toInt()
            if (diff in daysAgo) day.get(Calendar.DAY_OF_YEAR) else null
        }.distinct().size
    }

    private fun localDayRange(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return start to start + 86_400_000L
    }

    private fun sampleFoods() = listOf(
        FoodSearchResult("local-banana", "Banana", servingLabel = "1 medium", caloriesKcal = 105.0, proteinG = 1.3, carbohydratesG = 27.0, fatG = 0.4),
        FoodSearchResult("local-yoghurt", "Greek yoghurt", servingLabel = "170 g", caloriesKcal = 146.0, proteinG = 17.0, carbohydratesG = 8.0, fatG = 4.0),
        FoodSearchResult("local-paneer", "Paneer tikka", servingLabel = "1 bowl", caloriesKcal = 310.0, proteinG = 21.0, carbohydratesG = 13.0, fatG = 20.0),
    )
}
