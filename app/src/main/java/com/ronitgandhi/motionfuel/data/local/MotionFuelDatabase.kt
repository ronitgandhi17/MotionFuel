package com.ronitgandhi.motionfuel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkoutEntity::class,
        NutritionEntryEntity::class,
        UserProfileEntity::class,
        WeightEntryEntity::class,
        CustomMealEntity::class,
        DailySummaryEntity::class,
        MaintenanceSnapshotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MotionFuelDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun profileDao(): UserProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun customMealDao(): CustomMealDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun maintenanceSnapshotDao(): MaintenanceSnapshotDao
}
