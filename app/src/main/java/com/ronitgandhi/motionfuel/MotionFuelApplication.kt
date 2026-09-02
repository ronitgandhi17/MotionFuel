package com.ronitgandhi.motionfuel

import android.app.Application
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.ronitgandhi.motionfuel.config.AppConfig
import com.ronitgandhi.motionfuel.core.auth.AuthRepository
import com.ronitgandhi.motionfuel.data.local.MotionFuelDatabase
import com.ronitgandhi.motionfuel.data.network.ContextApiClient
import com.ronitgandhi.motionfuel.data.repository.MotionFuelRepository
import com.ronitgandhi.motionfuel.data.settings.SettingsRepository
import com.ronitgandhi.motionfuel.data.sync.FirestoreGateway

class MotionFuelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Lets AppConfig run its runtime FirebaseApp check without threading a Context to callers.
        AppConfig.appContextHolder = applicationContext
        // Registers the default FirebaseApp when google-services.json was present at build time.
        // Without it FirebaseApp.getApps() stays empty and the app shows the "configure Firebase" gate.
        FirebaseApp.initializeApp(this)
    }

    // Creates the offline Room database only when it is first requested.
    val database: MotionFuelDatabase by lazy {
        Room.databaseBuilder(this, MotionFuelDatabase::class.java, "motionfuel.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    // Exposes a single repository for workout, nutrition, profile, weight and summary persistence.
    val repository: MotionFuelRepository by lazy {
        MotionFuelRepository(
            database.workoutDao(),
            database.nutritionDao(),
            database.profileDao(),
            database.weightDao(),
            database.customMealDao(),
            database.dailySummaryDao(),
            database.maintenanceSnapshotDao(),
        )
    }
    // Stores lightweight user preferences through DataStore.
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    // Provides the weather and food network integrations (no key required).
    val apiClient: ContextApiClient by lazy { ContextApiClient() }
    // Wraps Firebase Authentication for the app-start identity gate.
    val authRepository: AuthRepository by lazy { AuthRepository() }
    // Reads and writes the signed-in user's private Firestore scope directly with the client SDK.
    val firestoreGateway: FirestoreGateway by lazy { FirestoreGateway() }
}
