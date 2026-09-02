package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.BiologicalSex
import kotlin.math.roundToInt

data class MaintenanceCaloriesResult(val bmrKcal: Double, val tdeeKcal: Int)

class CalculateMaintenanceCaloriesUseCase {
    // Applies the Mifflin–St Jeor equation and the selected activity multiplier.
    operator fun invoke(
        age: Int,
        sex: BiologicalSex,
        heightCm: Double,
        weightKg: Double,
        activityLevel: ActivityLevel,
    ): MaintenanceCaloriesResult {
        require(age in 13..120) { "Age must be between 13 and 120." }
        require(heightCm in 100.0..250.0) { "Height must be between 100 and 250 cm." }
        require(weightKg in 30.0..350.0) { "Weight must be between 30 and 350 kg." }
        val sexAdjustment = if (sex == BiologicalSex.MALE) 5.0 else -161.0
        val bmr = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + sexAdjustment
        return MaintenanceCaloriesResult(bmrKcal = bmr, tdeeKcal = (bmr * activityLevel.factor).roundToInt())
    }
}
