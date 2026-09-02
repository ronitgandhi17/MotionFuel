package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.BiologicalSex
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateMaintenanceCaloriesUseCaseTest {
    private val calculator = CalculateMaintenanceCaloriesUseCase()

    @Test
    fun maleFormulaUsesPositiveFiveAdjustment() {
        val result = calculator(23, BiologicalSex.MALE, 175.0, 75.0, ActivityLevel.SEDENTARY)
        assertEquals(1733.75, result.bmrKcal, 0.001)
        assertEquals(2081, result.tdeeKcal)
    }

    @Test
    fun femaleFormulaUsesNegativeOneHundredSixtyOneAdjustment() {
        val result = calculator(23, BiologicalSex.FEMALE, 165.0, 60.0, ActivityLevel.SEDENTARY)
        assertEquals(1355.25, result.bmrKcal, 0.001)
        assertEquals(1626, result.tdeeKcal)
    }

    @Test
    fun allActivityFactorsProduceExpectedRoundedTdee() {
        val expected = listOf(2081, 2384, 2687, 2991, 3294)
        ActivityLevel.entries.zip(expected).forEach { (level, tdee) ->
            assertEquals(tdee, calculator(23, BiologicalSex.MALE, 175.0, 75.0, level).tdeeKcal)
        }
    }
}
