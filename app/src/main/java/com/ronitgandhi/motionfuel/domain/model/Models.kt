package com.ronitgandhi.motionfuel.domain.model

enum class WorkoutType { WALK, RUN }
enum class ActivityType { STATIONARY, WALKING, RUNNING, UNKNOWN }
enum class LocationQuality { GOOD, FAIR, POOR }
enum class WorkoutStatus { IDLE, ACTIVE, PAUSED, COMPLETE }
enum class UnitSystem { METRIC, IMPERIAL }
enum class GoalType { CONSISTENCY, MAINTAIN, PERFORMANCE }
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }
enum class BiologicalSex { MALE, FEMALE }

enum class ActivityLevel(val factor: Double, val label: String) {
    SEDENTARY(1.2, "Sedentary"),
    LIGHT(1.375, "Lightly active"),
    MODERATE(1.55, "Moderately active"),
    VERY_ACTIVE(1.725, "Very active"),
    EXTREMELY_ACTIVE(1.9, "Extremely active"),
}

data class UserProfile(
    val userId: String,
    val name: String,
    val email: String,
    val age: Int,
    val sex: BiologicalSex,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val maintenanceCaloriesKcal: Int,
    val dailyCalorieGoalKcal: Int,
    val profileComplete: Boolean = true,
)

data class WeightEntry(
    val id: String,
    val weightKg: Double,
    val recordedAtMillis: Long,
)

data class TrendPoint(
    val timestampMillis: Long,
    val value: Double?,
    val target: Double? = null,
)

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

data class SavedFood(
    val id: String,
    val name: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val photoUri: String?,
    val createdAtMillis: Long,
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
    val isDemo: Boolean = false,
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
    val rejectedGpsPoints: Int = 0,
    val activeDaysThisWeek: Int = 0,
    val activeDaysPreviousWeek: Int = 0,
)

data class UserSettings(
    val units: UnitSystem = UnitSystem.METRIC,
    val routeBackupEnabled: Boolean = false,
    val darkTheme: Boolean = false,
    val weightKg: Double = 72.0,
    val goalType: GoalType = GoalType.CONSISTENCY,
)
