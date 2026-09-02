package com.ronitgandhi.motionfuel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val type: String,
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val averagePaceSecPerKm: Double?,
    val steps: Long,
    val elevationGainMeters: Double,
    val caloriesKcal: Double,
    val dominantActivity: String,
    val rejectedGpsPoints: Int,
    val routeJson: String,
    val syncState: String = "PENDING",
)

@Entity(tableName = "nutrition_entries")
data class NutritionEntryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val mealType: String,
    val consumedAtMillis: Long,
    val createdOffline: Boolean,
    val servingLabel: String = "1 serving",
    val quantity: Double = 1.0,
    val source: String = "SEARCH",
    val photoUri: String? = null,
    val syncState: String = "PENDING",
)

// Single-row cache of the signed-in user's profile so Today works offline. The uid is the PK.
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val displayName: String,
    val email: String?,
    val age: Int,
    val sex: String,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: String,
    val maintenanceCalories: Int,
    val dailyCalorieGoal: Int,
    val proteinTargetG: Double,
    val createdAtMillis: Long,
    val profileComplete: Boolean,
    val syncState: String = "PENDING",
)

@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val weightKg: Double,
    val recordedAtMillis: Long,
    val syncState: String = "PENDING",
)

@Entity(tableName = "custom_meals")
data class CustomMealEntity(
    @PrimaryKey val id: String,
    val name: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val photoUri: String?,
    val createdAtMillis: Long,
    val syncState: String = "PENDING",
)

// Per-day rollup keyed by a local yyyy-MM-dd date string; carries that day's target snapshot.
@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val dateKey: String,
    val calorieTarget: Int,
    val caloriesConsumed: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val breakfastKcal: Double,
    val lunchKcal: Double,
    val dinnerKcal: Double,
    val snackKcal: Double,
    val workoutBurnKcal: Double,
    val steps: Long,
    val latestWeightKg: Double?,
    val syncState: String = "PENDING",
)

@Entity(tableName = "maintenance_snapshots")
data class MaintenanceSnapshotEntity(
    @PrimaryKey val id: String,
    val calculatedAtMillis: Long,
    val bmr: Int,
    val tdee: Int,
    val weightKg: Double,
    val activityLevel: String,
    val trigger: String,
    val syncState: String = "PENDING",
)

data class NutritionTotalsProjection(
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
)
