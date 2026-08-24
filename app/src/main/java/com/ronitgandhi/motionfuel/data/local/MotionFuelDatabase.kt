package com.ronitgandhi.motionfuel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WorkoutEntity::class, NutritionEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MotionFuelDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun nutritionDao(): NutritionDao
}
