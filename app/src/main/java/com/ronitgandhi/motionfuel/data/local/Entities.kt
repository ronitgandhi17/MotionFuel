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
    val syncState: String = "PENDING",
)

data class NutritionTotalsProjection(
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
)

@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val weightKg: Double,
    val recordedAtMillis: Long,
    val syncState: String = "PENDING",
)

@Entity(tableName = "saved_foods")
data class SavedFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val photoUri: String?,
    val createdAtMillis: Long,
)

@Entity(tableName = "hydration_entries")
data class HydrationEntryEntity(
    @PrimaryKey val id: String,
    val amountMl: Int,
    val consumedAtMillis: Long,
)

@Entity(tableName = "meal_plan_entries")
data class MealPlanEntryEntity(
    @PrimaryKey val id: String,
    val scheduledDayStartMillis: Long,
    val mealType: String,
    val savedFoodId: String,
    val foodName: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ingredientsJson: String,
    val servings: Double,
    val createdAtMillis: Long,
)

@Entity(tableName = "planned_workouts")
data class PlannedWorkoutEntity(
    @PrimaryKey val id: String,
    val type: String,
    val scheduledAtMillis: Long,
    val targetDistanceMeters: Double,
    val targetDurationMinutes: Int,
    val completed: Boolean,
)

@Entity(tableName = "planned_routes")
data class PlannedRouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetDistanceMeters: Double,
    val routeJson: String,
    val createdAtMillis: Long,
    val availableOffline: Boolean,
)

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val metric: String,
    val target: Double,
    val progress: Double,
    val endsAtMillis: Long,
    val ownerUid: String,
)
