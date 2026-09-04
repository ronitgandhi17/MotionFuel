package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.BiologicalSex
import com.ronitgandhi.motionfuel.domain.model.ProfileUpdate
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileUpdateValidatorTest {
    private val valid = ProfileUpdate("Ronit", 24, BiologicalSex.MALE, 175.0, 72.0, ActivityLevel.MODERATE, 2_200)

    @Test
    fun acceptsValidEditableProfile() {
        assertNull(ProfileUpdateValidator.validate(valid))
    }

    @Test
    fun rejectsInvalidNameAndBodyRanges() {
        assertNotNull(ProfileUpdateValidator.validate(valid.copy(name = "R")))
        assertNotNull(ProfileUpdateValidator.validate(valid.copy(age = 12)))
        assertNotNull(ProfileUpdateValidator.validate(valid.copy(heightCm = 99.0)))
        assertNotNull(ProfileUpdateValidator.validate(valid.copy(weightKg = 351.0)))
    }

    @Test
    fun rejectsUnsafeCalorieGoal() {
        assertNotNull(ProfileUpdateValidator.validate(valid.copy(dailyCalorieGoalKcal = 10_001)))
    }
}
