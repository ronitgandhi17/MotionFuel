package com.ronitgandhi.motionfuel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WorkoutEntity::class, NutritionEntryEntity::class, WeightEntryEntity::class, SavedFoodEntity::class, HydrationEntryEntity::class, MealPlanEntryEntity::class, RecipeEntity::class, PlannedWorkoutEntity::class, PlannedRouteEntity::class, ChallengeEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class MotionFuelDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun weightDao(): WeightDao
    abstract fun savedFoodDao(): SavedFoodDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun recipeDao(): RecipeDao
    abstract fun plannedWorkoutDao(): PlannedWorkoutDao
    abstract fun plannedRouteDao(): PlannedRouteDao
    abstract fun challengeDao(): ChallengeDao
}
