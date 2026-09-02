package com.ronitgandhi.motionfuel.domain.model

enum class WorkoutType { WALK, RUN }
enum class ActivityType { STATIONARY, WALKING, RUNNING, UNKNOWN }
enum class LocationQuality { GOOD, FAIR, POOR }
enum class WorkoutStatus { IDLE, ACTIVE, PAUSED, COMPLETE }
enum class UnitSystem { METRIC, IMPERIAL }
enum class GoalType { CONSISTENCY, MAINTAIN, PERFORMANCE }
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

// Biological sex selector used by the Mifflin–St Jeor BMR equation.
enum class Sex { MALE, FEMALE }

// Activity multipliers applied to BMR to estimate total daily energy expenditure (TDEE).
enum class ActivityLevel(val factor: Double, val label: String, val detail: String) {
    SEDENTARY(1.2, "Sedentary", "Little or no exercise"),
    LIGHT(1.375, "Lightly active", "Light exercise 1–3 days/week"),
    MODERATE(1.55, "Moderately active", "Moderate exercise 3–5 days/week"),
    VERY_ACTIVE(1.725, "Very active", "Hard exercise 6–7 days/week"),
    EXTRA_ACTIVE(1.9, "Extremely active", "Physical job or twice-daily training"),
}

// Explains why a maintenance recalculation happened, for the Progress history trail.
enum class MaintenanceTrigger { INITIAL, WEIGHT_CHANGE, ACTIVITY_CHANGE, PROFILE_EDIT }

// How a logged nutrition entry originated, so the UI can label and edit it correctly.
enum class FoodSource { SEARCH, MANUAL, CUSTOM }


data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float = 5f,
    val timestampMillis: Long,
)

data class FilteredLocation(
    val raw: GeoPoint,
    val point: GeoPoint?,
    val accepted: Boolean,
    val quality: LocationQuality,
    val distanceDeltaMeters: Double = 0.0,
    val rejectionReason: String? = null,
)

data class SensorFeatureWindow(
    val accelerationEnergy: Double,
    val accelerationVariance: Double,
    val gyroscopeVariance: Double,
    val stepRatePerMinute: Double?,
    val gpsSpeedMps: Double?,
    val locationAccuracyMeters: Float?,
)

data class ActivityClassification(
    val type: ActivityType,
    val confidence: Float,
    val evidence: List<String>,
)

data class WeatherContext(
    val temperatureC: Double,
    val humidityPercent: Int,
    val windSpeedKph: Double,
    val isRaining: Boolean,
    val sourceAgeMinutes: Int = 0,
)

data class NutritionEntry(
    val id: String,
    val name: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val mealType: MealType,
    val consumedAtMillis: Long,
    val createdOffline: Boolean = false,
    // Human-readable serving the macros describe (e.g. "100 g", "1 bowl").
    val servingLabel: String = "1 serving",
    // Multiplier applied to the base serving when logged (2.0 = two servings).
    val quantity: Double = 1.0,
    // Where the entry came from, used for labelling and the Custom Meal photo.
    val source: FoodSource = FoodSource.SEARCH,
    // Optional local content-URI of a Custom Meal photo; never uploaded to the cloud.
    val photoUri: String? = null,
)

data class FoodSearchResult(
    val providerId: String,
    val name: String,
    val brand: String? = null,
    val servingLabel: String = "100 g",
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
)

data class NutritionTotals(
    val caloriesKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbohydratesG: Double = 0.0,
    val fatG: Double = 0.0,
)

data class WorkoutTelemetry(
    val status: WorkoutStatus = WorkoutStatus.IDLE,
    val type: WorkoutType = WorkoutType.RUN,
    val elapsedSeconds: Long = 0,
    val distanceMeters: Double = 0.0,
    val currentPaceSecPerKm: Double? = null,
    val averagePaceSecPerKm: Double? = null,
    val steps: Long = 0,
    val cadenceSpm: Int = 0,
    val elevationGainMeters: Double = 0.0,
    val caloriesKcal: Double = 0.0,
    val activity: ActivityClassification = ActivityClassification(ActivityType.UNKNOWN, 0f, emptyList()),
    val gpsQuality: LocationQuality = LocationQuality.POOR,
    val route: List<GeoPoint> = emptyList(),
    val rejectedGpsPoints: Int = 0,
)

data class WorkoutSummary(
    val id: String,
    val type: WorkoutType,
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val averagePaceSecPerKm: Double?,
    val steps: Long,
    val elevationGainMeters: Double,
    val caloriesKcal: Double,
    val dominantActivity: ActivityType,
    val rejectedGpsPoints: Int,
    val route: List<GeoPoint>,
)

enum class InsightCategory {
    PACE_DECLINE_WITH_HEAT,
    HIGH_ELEVATION_LOAD,
    LONGER_THAN_USUAL,
    RECOVERY_PROTEIN_CONTEXT,
    ENERGY_CONTEXT,
    LOCATION_QUALITY_LOW,
    GOAL_PROGRESS,
    GPS_DRIFT_CORRECTED,
}

enum class InsightPriority { LOW, MEDIUM, HIGH }

data class InsightEvidence(val label: String, val value: String)

data class Insight(
    val id: String,
    val category: InsightCategory,
    val title: String,
    val message: String,
    val priority: InsightPriority,
    val confidence: Float,
    val evidence: List<InsightEvidence>,
)

data class DailyContext(
    val workoutActive: Boolean = false,
    val paceDeclinePercent: Double = 0.0,
    val effortScore: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationBaselineMeters: Double = 35.0,
    val workoutDurationMinutes: Double = 0.0,
    val durationBaselineMinutes: Double = 30.0,
    val activeMinutesToday: Double = 0.0,
    val activityBaselineMinutes: Double = 35.0,
    val proteinLoggedG: Double = 0.0,
    val proteinTargetG: Double = 100.0,
    val caloriesLoggedKcal: Double = 0.0,
    val calorieTargetKcal: Double = 2200.0,
    val weather: WeatherContext? = null,
    val gpsQuality: LocationQuality = LocationQuality.GOOD,
    val rejectedGpsPoints: Int = 0,
    val activeDaysThisWeek: Int = 0,
    val activeDaysPreviousWeek: Int = 0,
)

data class UserSettings(
    val units: UnitSystem = UnitSystem.METRIC,
    val routeBackupEnabled: Boolean = false,
    val darkTheme: Boolean = true,
    val weightKg: Double = 72.0,
    val goalType: GoalType = GoalType.CONSISTENCY,
)

// The signed-in user's profile, mirrored to Firestore users/{uid} and cached in Room for offline.
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String? = null,
    val age: Int = 30,
    val sex: Sex = Sex.MALE,
    val heightCm: Double = 175.0,
    val weightKg: Double = 72.0,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    // Estimated maintenance calories from Mifflin–St Jeor × activity factor.
    val maintenanceCalories: Int = 0,
    // The user-editable daily calorie goal; may differ from maintenance and is never silently overwritten.
    val dailyCalorieGoal: Int = 0,
    val proteinTargetG: Double = 120.0,
    val createdAtMillis: Long = 0L,
    // True once the multi-step sign-up profile has been completed.
    val profileComplete: Boolean = false,
)

// A single body-weight measurement for the Progress trend chart.
data class WeightEntry(
    val id: String,
    val weightKg: Double,
    val recordedAtMillis: Long,
)

// A user-defined reusable meal with macros and an optional local photo.
data class CustomMeal(
    val id: String,
    val name: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val photoUri: String? = null,
    val createdAtMillis: Long,
)

// A per-day rollup with the calorie target snapshot taken on that day.
data class DailySummary(
    val dateKey: String,
    val calorieTarget: Int,
    val caloriesConsumed: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val breakfastKcal: Double = 0.0,
    val lunchKcal: Double = 0.0,
    val dinnerKcal: Double = 0.0,
    val snackKcal: Double = 0.0,
    val workoutBurnKcal: Double = 0.0,
    val steps: Long = 0,
    val latestWeightKg: Double? = null,
)

// A recorded BMR/TDEE calculation, kept so Progress can show how maintenance evolved.
data class MaintenanceSnapshot(
    val id: String,
    val calculatedAtMillis: Long,
    val bmr: Int,
    val tdee: Int,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val trigger: MaintenanceTrigger,
)
