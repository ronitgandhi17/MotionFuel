package com.ronitgandhi.motionfuel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ronitgandhi.motionfuel.domain.algorithm.AdaptiveInsightEngine
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.DailyContext
import com.ronitgandhi.motionfuel.domain.model.FoodSearchResult
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.service.WorkoutSessionController
import com.ronitgandhi.motionfuel.service.WorkoutTrackingService
import com.ronitgandhi.motionfuel.sync.SyncScheduler
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
    private val todayRange = localDayRange()
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

    // Weather is null until a real device location produces a live reading; nothing is fabricated.
    private val mutableWeather = MutableStateFlow<WeatherContext?>(null)
    val weather = mutableWeather.asStateFlow()
    private val mutableWeatherStatus = MutableStateFlow("Enable location for live weather")
    val weatherStatus = mutableWeatherStatus.asStateFlow()

    private val mutableFoodResults = MutableStateFlow<List<FoodSearchResult>>(emptyList())
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
                (if ((weather?.temperatureC ?: 0.0) >= 28) 0.20 else 0.0) +
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
                gpsQuality = workout.gpsQuality,
                rejectedGpsPoints = workout.rejectedGpsPoints,
                activeDaysThisWeek = distinctActiveDays(history, daysAgo = 0..6),
                activeDaysPreviousWeek = distinctActiveDays(history, daysAgo = 7..13),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshWeather()
        // Pulls any account data saved on other devices and drains local pending rows.
        SyncScheduler.enqueue(app)
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val location = lastKnownLocation()
            if (location == null) {
                mutableWeather.value = null
                mutableWeatherStatus.value = "Enable location for live weather"
                return@launch
            }
            mutableWeatherStatus.value = "Updating local conditions…"
            api.currentWeather(location.latitude, location.longitude)
                .onSuccess {
                    mutableWeather.value = it
                    mutableWeatherStatus.value = "Live context"
                }
                .onFailure { mutableWeatherStatus.value = "Weather unavailable • offline" }
        }
    }

    fun searchFoods(query: String) {
        if (query.isBlank()) {
            mutableFoodResults.value = emptyList()
            mutableFoodSearchStatus.value = null
            return
        }
        viewModelScope.launch {
            mutableFoodSearchStatus.value = "Searching Open Food Facts…"
            api.searchFoods(query)
                .onSuccess { results ->
                    mutableFoodResults.value = results
                    mutableFoodSearchStatus.value = if (results.isEmpty()) "No matches found for \"$query\"." else null
                }
                .onFailure {
                    mutableFoodResults.value = emptyList()
                    mutableFoodSearchStatus.value = "Search unavailable • check your connection."
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
                    createdOffline = mutableFoodSearchStatus.value?.contains("unavailable") == true,
                ),
            )
            // Queues the new nutrition row for background upload to the account cloud.
            SyncScheduler.enqueue(app)
        }
    }

    fun addManualFood(name: String, calories: Double, protein: Double, carbs: Double, fat: Double) {
        addFood(
            FoodSearchResult(
                providerId = "manual-${UUID.randomUUID()}",
                name = name.ifBlank { "Manual food" },
                servingLabel = "1 serving",
                caloriesKcal = calories.coerceAtLeast(0.0),
                proteinG = protein.coerceAtLeast(0.0),
                carbohydratesG = carbs.coerceAtLeast(0.0),
                fatG = fat.coerceAtLeast(0.0),
            ),
        )
    }

    fun startReal(type: WorkoutType) {
        workoutStartedAtMillis = System.currentTimeMillis()
        val intent = Intent(getApplication(), WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_START
            putExtra(WorkoutTrackingService.EXTRA_TYPE, type.name)
            putExtra(WorkoutTrackingService.EXTRA_WEIGHT_KG, settings.value.weightKg)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun pauseOrResumeWorkout() {
        val current = telemetry.value
        val action = if (current.status == WorkoutStatus.PAUSED) WorkoutTrackingService.ACTION_RESUME else WorkoutTrackingService.ACTION_PAUSE
        getApplication<Application>().startService(Intent(getApplication(), WorkoutTrackingService::class.java).setAction(action))
    }

    fun finishWorkout() {
        val snapshot = telemetry.value
        if (snapshot.status !in setOf(WorkoutStatus.ACTIVE, WorkoutStatus.PAUSED)) return
        getApplication<Application>().startService(
            Intent(getApplication(), WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_STOP),
        )
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
            // Queues the finished workout for background upload to the account cloud.
            SyncScheduler.enqueue(app)
        }
    }

    fun dismissCompletedWorkout() {
        WorkoutSessionController.reset()
    }

    fun setUnits(value: UnitSystem) = viewModelScope.launch { settingsRepository.setUnits(value) }
    fun setRouteBackup(value: Boolean) = viewModelScope.launch { settingsRepository.setRouteBackup(value) }
    fun setDarkTheme(value: Boolean) = viewModelScope.launch { settingsRepository.setDarkTheme(value) }
    fun deleteAllLocalData() = viewModelScope.launch { repository.deleteAllLocalData() }

    // Reads the best available last-known device location for weather, or null without permission.
    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val context = getApplication<Application>()
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (provider in locationManager.getProviders(true)) {
            val candidate = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || candidate.accuracy < best.accuracy) best = candidate
        }
        return best
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
}
