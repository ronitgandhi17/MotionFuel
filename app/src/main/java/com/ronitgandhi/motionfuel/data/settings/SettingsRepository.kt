package com.ronitgandhi.motionfuel.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ronitgandhi.motionfuel.domain.model.GoalType
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.UserSettings
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
    }

    val settings: Flow<UserSettings> = context.motionFuelDataStore.data.map { preferences ->
        UserSettings(
            units = preferences[Keys.Units]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.METRIC,
            routeBackupEnabled = preferences[Keys.RouteBackup] ?: false,
            darkTheme = preferences[Keys.DarkTheme] ?: true,
            weightKg = preferences[Keys.Weight] ?: 72.0,
            goalType = preferences[Keys.Goal]?.let { runCatching { GoalType.valueOf(it) }.getOrNull() } ?: GoalType.CONSISTENCY,
        )
    }

    suspend fun setUnits(value: UnitSystem) = context.motionFuelDataStore.edit { it[Keys.Units] = value.name }
    suspend fun setRouteBackup(value: Boolean) = context.motionFuelDataStore.edit { it[Keys.RouteBackup] = value }
    suspend fun setDarkTheme(value: Boolean) = context.motionFuelDataStore.edit { it[Keys.DarkTheme] = value }
}
