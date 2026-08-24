package com.ronitgandhi.motionfuel

import android.app.Application
import androidx.room.Room
import com.clerk.api.Clerk
import com.ronitgandhi.motionfuel.config.AppConfig
import com.ronitgandhi.motionfuel.data.local.MotionFuelDatabase
import com.ronitgandhi.motionfuel.data.network.ContextApiClient
import com.ronitgandhi.motionfuel.data.network.MembershipApiClient
import com.ronitgandhi.motionfuel.data.repository.MotionFuelRepository
import com.ronitgandhi.motionfuel.data.settings.SettingsRepository
import com.stripe.android.PaymentConfiguration

class MotionFuelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialises Clerk once when a real publishable key is available.
        if (AppConfig.isClerkConfigured) {
            Clerk.initialize(this, AppConfig.clerkPublishableKey)
        }
        // Initialises Stripe once so PaymentSheet can securely collect payment details.
        if (AppConfig.isStripeConfigured) {
            PaymentConfiguration.init(this, AppConfig.stripePublishableKey)
        }
    }

    // Creates the offline Room database only when it is first requested.
    val database: MotionFuelDatabase by lazy {
        Room.databaseBuilder(this, MotionFuelDatabase::class.java, "motionfuel.db")
            .build()
    }
    // Exposes a single repository for workout and nutrition persistence.
    val repository: MotionFuelRepository by lazy {
        MotionFuelRepository(database.workoutDao(), database.nutritionDao())
    }
    // Stores lightweight user preferences through DataStore.
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    // Provides the weather and food network integrations.
    val apiClient: ContextApiClient by lazy { ContextApiClient() }
    // Connects the Android app to the Clerk-protected Stripe membership server.
    val membershipApiClient: MembershipApiClient by lazy {
        MembershipApiClient(AppConfig.membershipApiBaseUrl)
    }
}
