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
import com.ronitgandhi.motionfuel.config.AppConfig
import com.ronitgandhi.motionfuel.domain.algorithm.AdaptiveInsightEngine
import com.ronitgandhi.motionfuel.domain.algorithm.MaintenanceCalculator
import com.ronitgandhi.motionfuel.domain.algorithm.NutritionMath
import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.CustomMeal
import com.ronitgandhi.motionfuel.domain.model.DailyContext
import com.ronitgandhi.motionfuel.domain.model.DailySummary
import com.ronitgandhi.motionfuel.domain.model.FoodSearchResult
import com.ronitgandhi.motionfuel.domain.model.FoodSource
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.MaintenanceSnapshot
import com.ronitgandhi.motionfuel.domain.model.MaintenanceTrigger
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.Sex
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.service.WorkoutSessionController
import com.ronitgandhi.motionfuel.service.WorkoutTrackingService
import com.ronitgandhi.motionfuel.sync.SyncScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Single AndroidViewModel backing both the authentication gate and the signed-in app. Everything
// is observed from Room (the offline source of truth); Firebase Auth provides identity and the
// Firestore gateway drains the sync queue in the background.
class MotionFuelViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MotionFuelApplication
    private val repository = app.repository
    private val settingsRepository = app.settingsRepository
    private val api = app.apiClient
    private val auth = app.authRepository
    private val insightEngine = AdaptiveInsightEngine()
    private val todayRange = localDayRange()
    private var workoutStartedAtMillis = 0L

    val firebaseConfigured: Boolean = AppConfig.isFirebaseConfigured

    // Identity: null when signed out. MainActivity routes the auth graph on this + profile.
    val authUser: StateFlow<com.google.firebase.auth.FirebaseUser?> =
        auth.authState.stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser)

    val profile: StateFlow<UserProfile?> =
        repository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val telemetry = WorkoutSessionController.telemetry
    val settings: StateFlow<UserSettings> = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings(),
    )
    val workouts: StateFlow<List<WorkoutSummary>> = repository.observeWorkouts().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val nutritionEntries: StateFlow<List<NutritionEntry>> =
        repository.observeNutritionEntries(todayRange.first, todayRange.second).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
        )
    val nutritionTotals: StateFlow<NutritionTotals> =
        repository.observeNutritionTotals(todayRange.first, todayRange.second).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionTotals(),
        )
    val weights: StateFlow<List<WeightEntry>> =
        repository.observeWeights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customMeals: StateFlow<List<CustomMeal>> =
        repository.observeCustomMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dailySummaries: StateFlow<List<DailySummary>> =
        repository.observeDailySummaries(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val maintenanceSnapshots: StateFlow<List<MaintenanceSnapshot>> =
        repository.observeMaintenanceSnapshots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableWeather = MutableStateFlow<WeatherContext?>(null)
    val weather = mutableWeather.asStateFlow()
    private val mutableWeatherStatus = MutableStateFlow("Enable location for live weather")
    val weatherStatus = mutableWeatherStatus.asStateFlow()

    private val mutableFoodResults = MutableStateFlow<List<FoodSearchResult>>(emptyList())
    val foodResults = mutableFoodResults.asStateFlow()
    private val mutableFoodSearchStatus = MutableStateFlow<String?>(null)
    val foodSearchStatus = mutableFoodSearchStatus.asStateFlow()

    // Auth form feedback shared by the login/sign-up/reset screens.
    private val mutableAuthError = MutableStateFlow<String?>(null)
    val authError = mutableAuthError.asStateFlow()
    private val mutableAuthBusy = MutableStateFlow(false)
    val authBusy = mutableAuthBusy.asStateFlow()
    private val mutableAuthNotice = MutableStateFlow<String?>(null)
    val authNotice = mutableAuthNotice.asStateFlow()

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
                proteinTargetG = profile.value?.proteinTargetG ?: 120.0,
                caloriesLoggedKcal = nutrition.caloriesKcal,
                calorieTargetKcal = (profile.value?.dailyCalorieGoal ?: 2200).toDouble().coerceAtLeast(1.0),
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
        // Pull any account data saved on other devices and drain local pending rows.
        SyncScheduler.enqueue(app)
    }

    // ---- Authentication (forms delegate here; MainActivity routes on authUser + profile) ----
    fun signIn(email: String, password: String) {
        runAuth { auth.signIn(email, password).map { } }
    }

    // Creates the Firebase account, sets the display name, computes maintenance from the collected
    // profile, and writes the profile locally (PENDING) so the app is usable offline immediately.
    fun signUp(
        name: String,
        email: String,
        password: String,
        age: Int,
        sex: Sex,
        heightCm: Double,
        weightKg: Double,
        activityLevel: ActivityLevel,
        goalOverride: Int? = null,
    ) {
        runAuth {
            auth.signUp(email, password).mapCatching { user ->
                auth.updateDisplayName(name)
                val maintenance = MaintenanceCalculator.estimate(sex, weightKg, heightCm, age, activityLevel)
                val goal = goalOverride?.takeIf { it > 0 } ?: MaintenanceCalculator.defaultGoalFor(maintenance.tdee)
                val profileModel = UserProfile(
                    uid = user.uid,
                    displayName = name,
                    email = user.email ?: email.trim(),
                    age = age,
                    sex = sex,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    activityLevel = activityLevel,
                    maintenanceCalories = maintenance.tdee,
                    dailyCalorieGoal = goal,
                    proteinTargetG = MaintenanceCalculator.proteinTargetGrams(weightKg),
                    createdAtMillis = System.currentTimeMillis(),
                    profileComplete = true,
                )
                repository.saveProfile(profileModel)
                settingsRepository.setWeight(weightKg)
                repository.saveMaintenanceSnapshot(
                    MaintenanceSnapshot(
                        id = UUID.randomUUID().toString(),
                        calculatedAtMillis = System.currentTimeMillis(),
                        bmr = maintenance.bmr,
                        tdee = maintenance.tdee,
                        weightKg = weightKg,
                        activityLevel = activityLevel,
                        trigger = MaintenanceTrigger.INITIAL,
                    ),
                )
                SyncScheduler.enqueue(app)
            }
        }
    }

    // Writes a profile for an already–signed-in user (e.g. an existing account on a fresh install
    // whose cloud profile has not yet been pulled). Mirrors the sign-up profile write.
    fun completeProfile(
        age: Int,
        sex: Sex,
        heightCm: Double,
        weightKg: Double,
        activityLevel: ActivityLevel,
    ) {
        val user = auth.currentUser ?: return
        runAuth {
            runCatching {
                val maintenance = MaintenanceCalculator.estimate(sex, weightKg, heightCm, age, activityLevel)
                repository.saveProfile(
                    UserProfile(
                        uid = user.uid,
                        displayName = user.displayName ?: (user.email?.substringBefore("@") ?: "Athlete"),
                        email = user.email,
                        age = age,
                        sex = sex,
                        heightCm = heightCm,
                        weightKg = weightKg,
                        activityLevel = activityLevel,
                        maintenanceCalories = maintenance.tdee,
                        dailyCalorieGoal = MaintenanceCalculator.defaultGoalFor(maintenance.tdee),
                        proteinTargetG = MaintenanceCalculator.proteinTargetGrams(weightKg),
                        createdAtMillis = System.currentTimeMillis(),
                        profileComplete = true,
                    ),
                )
                settingsRepository.setWeight(weightKg)
                repository.saveMaintenanceSnapshot(
                    MaintenanceSnapshot(
                        id = UUID.randomUUID().toString(),
                        calculatedAtMillis = System.currentTimeMillis(),
                        bmr = maintenance.bmr,
                        tdee = maintenance.tdee,
                        weightKg = weightKg,
                        activityLevel = activityLevel,
                        trigger = MaintenanceTrigger.INITIAL,
                    ),
                )
                SyncScheduler.enqueue(app)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        runAuth(successNotice = "Password reset email sent. Check your inbox.") { auth.sendPasswordReset(email) }
    }

    fun resendVerification() {
        runAuth(successNotice = "Verification email sent.") { auth.sendEmailVerification() }
    }

    fun refreshVerification() {
        viewModelScope.launch { auth.reload() }
    }

    fun signOut() {
        auth.signOut()
        clearAuthFeedback()
    }

    fun clearAuthFeedback() {
        mutableAuthError.value = null
        mutableAuthNotice.value = null
    }

    // Deletes the cloud scope, the Firebase account, and every local row.
    fun deleteAccount(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val uid = auth.uid
            if (uid != null && app.firestoreGateway.isAvailable) {
                runCatching { app.firestoreGateway.deleteUserScope(uid) }
            }
            repository.deleteAllLocalData()
            val result = auth.deleteAccount()
            onComplete(result.isSuccess)
        }
    }

    private fun runAuth(successNotice: String? = null, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            mutableAuthBusy.value = true
            mutableAuthError.value = null
            mutableAuthNotice.value = null
            block()
                .onSuccess { mutableAuthNotice.value = successNotice }
                .onFailure { mutableAuthError.value = it.message ?: "Something went wrong. Try again." }
            mutableAuthBusy.value = false
        }
    }

    // ---- Weather / food search (Open-Meteo + Open Food Facts, no key required) ----
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

    fun clearFoodResults() {
        mutableFoodResults.value = emptyList()
        mutableFoodSearchStatus.value = null
    }

    // ---- Nutrition logging (per meal slot, with quantity scaling) ----
    fun addFood(food: FoodSearchResult, mealType: MealType = MealType.SNACK, quantity: Double = 1.0) {
        val q = quantity.coerceAtLeast(0.0)
        viewModelScope.launch {
            repository.saveNutrition(
                NutritionEntry(
                    id = UUID.randomUUID().toString(),
                    name = food.name,
                    caloriesKcal = food.caloriesKcal * q,
                    proteinG = food.proteinG * q,
                    carbohydratesG = food.carbohydratesG * q,
                    fatG = food.fatG * q,
                    mealType = mealType,
                    consumedAtMillis = System.currentTimeMillis(),
                    createdOffline = mutableFoodSearchStatus.value?.contains("unavailable") == true,
                    servingLabel = food.servingLabel,
                    quantity = q,
                    source = FoodSource.SEARCH,
                ),
            )
            aggregateToday()
            SyncScheduler.enqueue(app)
        }
    }

    fun addManualFood(name: String, calories: Double, protein: Double, carbs: Double, fat: Double, mealType: MealType = MealType.SNACK) {
        viewModelScope.launch {
            repository.saveNutrition(
                NutritionEntry(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Manual food" },
                    caloriesKcal = calories.coerceAtLeast(0.0),
                    proteinG = protein.coerceAtLeast(0.0),
                    carbohydratesG = carbs.coerceAtLeast(0.0),
                    fatG = fat.coerceAtLeast(0.0),
                    mealType = mealType,
                    consumedAtMillis = System.currentTimeMillis(),
                    servingLabel = "1 serving",
                    quantity = 1.0,
                    source = FoodSource.MANUAL,
                ),
            )
            aggregateToday()
            SyncScheduler.enqueue(app)
        }
    }

    fun deleteFood(id: String) {
        viewModelScope.launch {
            repository.deleteNutrition(id)
            aggregateToday()
        }
    }

    // ---- Custom meals (reusable; macros prefill calories via 4/4/9; photo stays local) ----
    fun saveCustomMeal(name: String, calories: Double, protein: Double, carbs: Double, fat: Double, photoUri: String?) {
        viewModelScope.launch {
            repository.saveCustomMeal(
                CustomMeal(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Custom meal" },
                    caloriesKcal = calories.coerceAtLeast(0.0),
                    proteinG = protein.coerceAtLeast(0.0),
                    carbohydratesG = carbs.coerceAtLeast(0.0),
                    fatG = fat.coerceAtLeast(0.0),
                    photoUri = photoUri,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            SyncScheduler.enqueue(app)
        }
    }

    fun deleteCustomMeal(id: String) {
        viewModelScope.launch { repository.deleteCustomMeal(id) }
    }

    fun logCustomMeal(meal: CustomMeal, mealType: MealType, quantity: Double = 1.0) {
        val q = quantity.coerceAtLeast(0.0)
        viewModelScope.launch {
            repository.saveNutrition(
                NutritionEntry(
                    id = UUID.randomUUID().toString(),
                    name = meal.name,
                    caloriesKcal = meal.caloriesKcal * q,
                    proteinG = meal.proteinG * q,
                    carbohydratesG = meal.carbohydratesG * q,
                    fatG = meal.fatG * q,
                    mealType = mealType,
                    consumedAtMillis = System.currentTimeMillis(),
                    servingLabel = "1 serving",
                    quantity = q,
                    source = FoodSource.CUSTOM,
                    photoUri = meal.photoUri,
                ),
            )
            aggregateToday()
            SyncScheduler.enqueue(app)
        }
    }

    // ---- Goal + maintenance recalculation (never silently overwrites a custom goal) ----
    fun updateDailyGoal(goalKcal: Int) {
        val current = profile.value ?: return
        viewModelScope.launch {
            repository.saveProfile(current.copy(dailyCalorieGoal = goalKcal.coerceAtLeast(0)))
            SyncScheduler.enqueue(app)
        }
    }

    fun addWeight(weightKg: Double) {
        val kg = weightKg.coerceIn(25.0, 400.0)
        viewModelScope.launch {
            repository.saveWeight(
                WeightEntry(id = UUID.randomUUID().toString(), weightKg = kg, recordedAtMillis = System.currentTimeMillis()),
            )
            settingsRepository.setWeight(kg)
            recomputeMaintenance(weightKg = kg, trigger = MaintenanceTrigger.WEIGHT_CHANGE)
            SyncScheduler.enqueue(app)
        }
    }

    fun updateActivityLevel(level: ActivityLevel) {
        viewModelScope.launch {
            recomputeMaintenance(activityLevel = level, trigger = MaintenanceTrigger.ACTIVITY_CHANGE)
            SyncScheduler.enqueue(app)
        }
    }

    fun editProfileBasics(age: Int, sex: Sex, heightCm: Double) {
        viewModelScope.launch {
            recomputeMaintenance(age = age, sex = sex, heightCm = heightCm, trigger = MaintenanceTrigger.PROFILE_EDIT)
            SyncScheduler.enqueue(app)
        }
    }

    // Recomputes BMR/TDEE from the (possibly updated) profile fields, records a snapshot, and only
    // moves the daily goal when it was still tracking the old maintenance figure.
    private suspend fun recomputeMaintenance(
        weightKg: Double? = null,
        activityLevel: ActivityLevel? = null,
        age: Int? = null,
        sex: Sex? = null,
        heightCm: Double? = null,
        trigger: MaintenanceTrigger,
    ) {
        val current = repository.currentProfile() ?: return
        val updated = current.copy(
            weightKg = weightKg ?: current.weightKg,
            activityLevel = activityLevel ?: current.activityLevel,
            age = age ?: current.age,
            sex = sex ?: current.sex,
            heightCm = heightCm ?: current.heightCm,
        )
        val maintenance = MaintenanceCalculator.estimate(
            updated.sex, updated.weightKg, updated.heightCm, updated.age, updated.activityLevel,
        )
        // A goal still equal to the previous maintenance (or unset) is considered "not customised".
        val goalTracksMaintenance = current.dailyCalorieGoal <= 0 || current.dailyCalorieGoal == current.maintenanceCalories
        val newGoal = if (goalTracksMaintenance) maintenance.tdee else current.dailyCalorieGoal
        repository.saveProfile(
            updated.copy(
                maintenanceCalories = maintenance.tdee,
                dailyCalorieGoal = newGoal,
                proteinTargetG = MaintenanceCalculator.proteinTargetGrams(updated.weightKg),
            ),
        )
        repository.saveMaintenanceSnapshot(
            MaintenanceSnapshot(
                id = UUID.randomUUID().toString(),
                calculatedAtMillis = System.currentTimeMillis(),
                bmr = maintenance.bmr,
                tdee = maintenance.tdee,
                weightKg = updated.weightKg,
                activityLevel = updated.activityLevel,
                trigger = trigger,
            ),
        )
    }

    // ---- Workout tracking (foreground service; telemetry bridged via WorkoutSessionController) ----
    fun startReal(type: WorkoutType) {
        workoutStartedAtMillis = System.currentTimeMillis()
        val intent = Intent(getApplication(), WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_START
            putExtra(WorkoutTrackingService.EXTRA_TYPE, type.name)
            putExtra(WorkoutTrackingService.EXTRA_WEIGHT_KG, profile.value?.weightKg ?: settings.value.weightKg)
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
            aggregateToday()
            SyncScheduler.enqueue(app)
        }
    }

    fun dismissCompletedWorkout() {
        WorkoutSessionController.reset()
    }

    // ---- Settings ----
    fun setUnits(value: UnitSystem) = viewModelScope.launch { settingsRepository.setUnits(value) }
    fun setRouteBackup(value: Boolean) = viewModelScope.launch { settingsRepository.setRouteBackup(value) }
    fun setDarkTheme(value: Boolean) = viewModelScope.launch { settingsRepository.setDarkTheme(value) }
    fun deleteAllLocalData() = viewModelScope.launch { repository.deleteAllLocalData() }

    // Rolls today's entries + workouts into a DailySummary carrying the day's calorie-target snapshot.
    private suspend fun aggregateToday() {
        val profileSnapshot = repository.currentProfile()
        val entries = repository.observeNutritionEntries(todayRange.first, todayRange.second).first()
        val totals = NutritionMath.totalsOf(entries)
        val workoutBurn = repository.observeWorkouts().first()
            .filter { it.startedAtMillis >= todayRange.first && it.startedAtMillis < todayRange.second }
            .sumOf { it.caloriesKcal }
        val latestWeight = repository.observeWeights().first().maxByOrNull { it.recordedAtMillis }?.weightKg
        val steps = repository.observeWorkouts().first()
            .filter { it.startedAtMillis >= todayRange.first && it.startedAtMillis < todayRange.second }
            .sumOf { it.steps }
        repository.upsertDailySummary(
            DailySummary(
                dateKey = dateKeyFor(todayRange.first),
                calorieTarget = profileSnapshot?.dailyCalorieGoal ?: 0,
                caloriesConsumed = totals.caloriesKcal,
                proteinG = totals.proteinG,
                carbohydratesG = totals.carbohydratesG,
                fatG = totals.fatG,
                breakfastKcal = NutritionMath.caloriesForMeal(entries, MealType.BREAKFAST),
                lunchKcal = NutritionMath.caloriesForMeal(entries, MealType.LUNCH),
                dinnerKcal = NutritionMath.caloriesForMeal(entries, MealType.DINNER),
                snackKcal = NutritionMath.caloriesForMeal(entries, MealType.SNACK),
                workoutBurnKcal = workoutBurn,
                steps = steps,
                latestWeightKg = latestWeight,
            ),
        )
        SyncScheduler.enqueue(app)
    }

    // ---- Helpers ----
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

    private fun dateKeyFor(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(millis))

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
