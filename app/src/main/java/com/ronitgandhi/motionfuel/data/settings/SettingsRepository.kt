package com.ronitgandhi.motionfuel.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ronitgandhi.motionfuel.domain.model.GoalType
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserSettings
import com.ronitgandhi.motionfuel.domain.model.WellnessGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.motionFuelDataStore by preferencesDataStore(name = "motionfuel")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val Units = stringPreferencesKey("units")
        val RouteBackup = booleanPreferencesKey("route_backup")
        val DarkTheme = booleanPreferencesKey("dark_theme")
        val Weight = doublePreferencesKey("weight_kg")
        val Goal = stringPreferencesKey("goal")
        val WeeklyWorkoutTarget = intPreferencesKey("weekly_workout_target")
        val DailyStepTarget = intPreferencesKey("daily_step_target")
        val DailyWaterTarget = intPreferencesKey("daily_water_target_ml")
        val SleepHours = doublePreferencesKey("manual_sleep_hours")
        val RestingHeartRate = intPreferencesKey("manual_resting_heart_rate")
        val HealthConnect = booleanPreferencesKey("health_connect_enabled")
        val WearableSync = booleanPreferencesKey("wearable_sync_enabled")
    }

    val settings: Flow<UserSettings> = context.motionFuelDataStore.data.map { preferences ->
        UserSettings(
            units = preferences[Keys.Units]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.METRIC,
            routeBackupEnabled = preferences[Keys.RouteBackup] ?: false,
            darkTheme = preferences[Keys.DarkTheme] ?: true,
            weightKg = preferences[Keys.Weight] ?: 72.0,
            goalType = preferences[Keys.Goal]?.let { runCatching { GoalType.valueOf(it) }.getOrNull() } ?: GoalType.CONSISTENCY,
            wellnessGoals = WellnessGoals(
                weeklyWorkoutTarget = preferences[Keys.WeeklyWorkoutTarget] ?: 3,
                dailyStepTarget = preferences[Keys.DailyStepTarget] ?: 10_000,
                dailyWaterTargetMl = preferences[Keys.DailyWaterTarget] ?: 2_500,
            ),
            manualSleepHours = preferences[Keys.SleepHours] ?: 7.5,
            manualRestingHeartRateBpm = preferences[Keys.RestingHeartRate] ?: 68,
            healthConnectEnabled = preferences[Keys.HealthConnect] ?: false,
            wearableSyncEnabled = preferences[Keys.WearableSync] ?: false,
        )
    }

    suspend fun setUnits(value: UnitSystem) = context.motionFuelDataStore.edit { it[Keys.Units] = value.name }
    suspend fun setRouteBackup(value: Boolean) = context.motionFuelDataStore.edit { it[Keys.RouteBackup] = value }
    suspend fun setDarkTheme(value: Boolean) = context.motionFuelDataStore.edit { it[Keys.DarkTheme] = value }
    suspend fun setWeight(value: Double) = context.motionFuelDataStore.edit { it[Keys.Weight] = value }
    suspend fun setWellnessGoals(value: WellnessGoals) = context.motionFuelDataStore.edit {
        it[Keys.WeeklyWorkoutTarget] = value.weeklyWorkoutTarget.coerceIn(1, 14)
        it[Keys.DailyStepTarget] = value.dailyStepTarget.coerceIn(1_000, 100_000)
        it[Keys.DailyWaterTarget] = value.dailyWaterTargetMl.coerceIn(500, 8_000)
    }
    suspend fun setRecoveryInputs(sleepHours: Double, restingHeartRate: Int) = context.motionFuelDataStore.edit {
        it[Keys.SleepHours] = sleepHours.coerceIn(0.0, 16.0)
        it[Keys.RestingHeartRate] = restingHeartRate.coerceIn(30, 220)
    }
    suspend fun setHealthConnectEnabled(value: Boolean) = context.motionFuelDataStore.edit { it[Keys.HealthConnect] = value }
    suspend fun setWearableSyncEnabled(value: Boolean) = context.motionFuelDataStore.edit { it[Keys.WearableSync] = value }
}
