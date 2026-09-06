package com.ronitgandhi.motionfuel.domain.model

enum class RecordSource { MOTIONFUEL, PHONE_SENSOR, HEALTH_CONNECT }

data class MealRecommendation(
    val foodId: String,
    val name: String,
    val reason: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val score: Double,
)

data class RecipeIngredient(
    val name: String,
    val servings: Double,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
)

data class Recipe(
    val id: String,
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val servings: Double,
    val createdAtMillis: Long,
) {
    val caloriesPerServing: Double get() = ingredients.sumOf { it.caloriesKcal * it.servings } / servings.coerceAtLeast(1.0)
    val proteinPerServing: Double get() = ingredients.sumOf { it.proteinG * it.servings } / servings.coerceAtLeast(1.0)
    val carbohydratesPerServing: Double get() = ingredients.sumOf { it.carbohydratesG * it.servings } / servings.coerceAtLeast(1.0)
    val fatPerServing: Double get() = ingredients.sumOf { it.fatG * it.servings } / servings.coerceAtLeast(1.0)
}

data class PlannedWorkout(
    val id: String,
    val type: WorkoutType,
    val scheduledAtMillis: Long,
    val targetDistanceMeters: Double,
    val targetDurationMinutes: Int,
    val completed: Boolean = false,
)

data class PlannedRoute(
    val id: String,
    val name: String,
    val targetDistanceMeters: Double,
    val points: List<GeoPoint>,
    val createdAtMillis: Long,
    val availableOffline: Boolean = true,
)

data class ExerciseZone(
    val label: String,
    val seconds: Long,
    val colourIndex: Int,
)

data class ProgressPrediction(
    val summary: String,
    val projectedDateMillis: Long?,
    val confidence: String,
)

data class Challenge(
    val id: String,
    val title: String,
    val metric: String,
    val target: Double,
    val progress: Double,
    val endsAtMillis: Long,
    val ownerUid: String,
)

data class ChallengeStanding(val uid: String, val displayName: String, val score: Double)

data class SafetyShareSession(
    val token: String,
    val expiresAtMillis: Long,
    val active: Boolean,
    val lastPoint: GeoPoint?,
)

data class SmartReminderPreferences(
    val enabled: Boolean = false,
    val inactivity: Boolean = true,
    val hydration: Boolean = true,
    val mealGap: Boolean = true,
    val weather: Boolean = true,
)
