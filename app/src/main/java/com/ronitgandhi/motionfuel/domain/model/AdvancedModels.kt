package com.ronitgandhi.motionfuel.domain.model

data class AdaptiveFuelTarget(
    val baselineKcal: Int,
    val recommendedKcal: Int,
    val adjustmentKcal: Int,
    val hydrationTargetMl: Int,
    val reasons: List<String>,
)

data class RecoveryScore(
    val score: Int,
    val label: String,
    val recommendation: String,
    val factors: List<String>,
)

data class WorkoutSplit(
    val kilometre: Int,
    val durationSeconds: Long,
    val paceSecondsPerKm: Double,
    val elevationDeltaMeters: Double,
)

data class PersonalRecords(
    val longestDistanceMeters: Double = 0.0,
    val longestDurationSeconds: Long = 0,
    val fastestPaceSecPerKm: Double? = null,
    val mostSteps: Long = 0,
    val currentWorkoutStreakDays: Int = 0,
)

data class HydrationEntry(
    val id: String,
    val amountMl: Int,
    val consumedAtMillis: Long,
)

data class MealPlanEntry(
    val id: String,
    val scheduledDayStartMillis: Long,
    val mealType: MealType,
    val savedFoodId: String,
    val foodName: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
)

data class WellnessGoals(
    val weeklyWorkoutTarget: Int = 3,
    val dailyStepTarget: Int = 10_000,
    val dailyWaterTargetMl: Int = 2_500,
)

data class GoalProgress(
    val workoutDays: Int,
    val workoutTarget: Int,
    val stepsToday: Long,
    val stepTarget: Int,
    val waterTodayMl: Int,
    val waterTargetMl: Int,
    val workoutStreakDays: Int,
)

data class WeeklyReport(
    val workouts: Int,
    val activeMinutes: Long,
    val distanceMeters: Double,
    val exerciseCaloriesKcal: Double,
    val averageFoodCaloriesKcal: Double,
    val proteinAverageG: Double,
    val weightChangeKg: Double?,
    val consistencyScore: Int,
    val focus: String,
)

data class ConnectedHealthSnapshot(
    val available: Boolean = false,
    val permissionsGranted: Boolean = false,
    val stepsToday: Long = 0,
    val activeCaloriesKcal: Double = 0.0,
    val latestWeightKg: Double? = null,
    val sleepHours: Double? = null,
    val restingHeartRateBpm: Int? = null,
    val status: String = "Not connected",
)
