package com.ronitgandhi.motionfuel

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.ronitgandhi.motionfuel.data.local.MotionFuelDatabase
import com.ronitgandhi.motionfuel.data.network.ContextApiClient
import com.ronitgandhi.motionfuel.data.repository.MotionFuelRepository
import com.ronitgandhi.motionfuel.data.settings.SettingsRepository
import com.ronitgandhi.motionfuel.share.ActivityShareImage
import com.ronitgandhi.motionfuel.share.FoodShareImage

class MotionFuelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ActivityShareImage.deleteExpiredFiles(this)
        FoodShareImage.deleteExpiredFiles(this)
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(MotionFuelAppCheckProvider.factory())
            com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val consent = auth.currentUser?.uid?.let { com.ronitgandhi.motionfuel.data.features.FeatureStore(this, it).data.value.optBoolean("diagnostics", false) } ?: false
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(consent)
                com.google.firebase.perf.FirebasePerformance.getInstance().isPerformanceCollectionEnabled = consent
            }
        }
    }

    // Creates the offline Room database only when it is first requested.
    val database: MotionFuelDatabase by lazy {
        Room.databaseBuilder(this, MotionFuelDatabase::class.java, "motionfuel.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            // Recreates incompatible development schemas instead of crashing when the home screen opens.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    // Exposes a single repository for workout, nutrition and weight persistence.
    val repository: MotionFuelRepository by lazy {
        MotionFuelRepository(database.workoutDao(), database.nutritionDao(), database.weightDao(), database.savedFoodDao(), database.hydrationDao(), database.mealPlanDao(), database.recipeDao(), database.plannedWorkoutDao(), database.plannedRouteDao(), database.challengeDao())
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

        // Adds reusable manual foods while preserving all existing workout and diary data.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_foods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `caloriesKcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbohydratesG` REAL NOT NULL, `fatG` REAL NOT NULL, `photoUri` TEXT, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `hydration_entries` (`id` TEXT NOT NULL, `amountMl` INTEGER NOT NULL, `consumedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `meal_plan_entries` (`id` TEXT NOT NULL, `scheduledDayStartMillis` INTEGER NOT NULL, `mealType` TEXT NOT NULL, `savedFoodId` TEXT NOT NULL, `foodName` TEXT NOT NULL, `caloriesKcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbohydratesG` REAL NOT NULL, `fatG` REAL NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `ingredientsJson` TEXT NOT NULL, `servings` REAL NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `planned_workouts` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `scheduledAtMillis` INTEGER NOT NULL, `targetDistanceMeters` REAL NOT NULL, `targetDurationMinutes` INTEGER NOT NULL, `completed` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `planned_routes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `targetDistanceMeters` REAL NOT NULL, `routeJson` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `availableOffline` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `challenges` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `metric` TEXT NOT NULL, `target` REAL NOT NULL, `progress` REAL NOT NULL, `endsAtMillis` INTEGER NOT NULL, `ownerUid` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
