package com.ronitgandhi.motionfuel

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ronitgandhi.motionfuel.data.local.MotionFuelDatabase
import com.ronitgandhi.motionfuel.data.network.ContextApiClient
import com.ronitgandhi.motionfuel.data.repository.MotionFuelRepository
import com.ronitgandhi.motionfuel.data.settings.SettingsRepository

class MotionFuelApplication : Application() {
    // Creates the offline Room database only when it is first requested.
    val database: MotionFuelDatabase by lazy {
        Room.databaseBuilder(this, MotionFuelDatabase::class.java, "motionfuel.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    // Exposes a single repository for workout, nutrition and weight persistence.
    val repository: MotionFuelRepository by lazy {
        MotionFuelRepository(database.workoutDao(), database.nutritionDao(), database.weightDao())
    }
    // Stores lightweight user preferences through DataStore.
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    // Provides the weather and food network integrations.
    val apiClient: ContextApiClient by lazy { ContextApiClient() }
    companion object {
        // Adds local weight history without deleting existing workout and nutrition records.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `weight_entries` (`id` TEXT NOT NULL, `weightKg` REAL NOT NULL, `recordedAtMillis` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
