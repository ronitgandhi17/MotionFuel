package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ProfileUpdate

object ProfileUpdateValidator {
    // Applies the same profile limits enforced by Firestore before attempting a network write.
    fun validate(update: ProfileUpdate): String? = when {
        update.name.trim().length !in 2..100 -> "Enter a name between 2 and 100 characters."
        update.age !in 13..120 -> "Enter an age between 13 and 120."
        update.heightCm !in 100.0..250.0 -> "Enter a height between 100 and 250 cm."
        update.weightKg !in 30.0..350.0 -> "Enter a weight between 30 and 350 kg."
        update.dailyCalorieGoalKcal !in 100..10_000 -> "Enter a daily calorie goal between 100 and 10,000 kcal."
        else -> null
    }
}
