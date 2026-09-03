package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.BiologicalSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceCaloriesBoundaryTest {
    private val calculator = CalculateMaintenanceCaloriesUseCase()

    @Test
    fun acceptedInputBoundariesProduceFinitePositiveResults() {
        val cases = listOf(
            Triple(13, 100.0, 30.0),
            Triple(120, 250.0, 350.0),
        )
        cases.forEach { (age, height, weight) ->
            BiologicalSex.entries.forEach { sex ->
                ActivityLevel.entries.forEach { level ->
                    val result = calculator(age, sex, height, weight, level)
                    assertTrue(result.bmrKcal.isFinite())
                    assertTrue(result.bmrKcal > 0.0)
                    assertTrue(result.tdeeKcal > 0)
                }
            }
        }
    }

    @Test
    fun agesOutsideSupportedRangeAreRejected() {
        listOf(12, 121, Int.MIN_VALUE, Int.MAX_VALUE).forEach { age ->
            assertThrows(IllegalArgumentException::class.java) {
                calculator(age, BiologicalSex.MALE, 175.0, 75.0, ActivityLevel.MODERATE)
            }
        }
    }

    @Test
    fun heightsOutsideSupportedRangeAreRejected() {
        listOf(99.99, 250.01, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN).forEach { height ->
            assertThrows(IllegalArgumentException::class.java) {
                calculator(23, BiologicalSex.MALE, height, 75.0, ActivityLevel.MODERATE)
            }
        }
    }

    @Test
    fun weightsOutsideSupportedRangeAreRejected() {
        listOf(29.99, 350.01, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN).forEach { weight ->
            assertThrows(IllegalArgumentException::class.java) {
                calculator(23, BiologicalSex.MALE, 175.0, weight, ActivityLevel.MODERATE)
            }
        }
    }

    @Test
    fun activityLevelsIncreaseTdeeMonotonically() {
        val values = ActivityLevel.entries.map { calculator(30, BiologicalSex.FEMALE, 165.0, 65.0, it).tdeeKcal }
        values.zipWithNext().forEach { (lower, higher) -> assertTrue(higher > lower) }
    }

    @Test
    fun sexAdjustmentDifferenceIsExactlyOneHundredSixtySixCalories() {
        val male = calculator(30, BiologicalSex.MALE, 175.0, 75.0, ActivityLevel.SEDENTARY)
        val female = calculator(30, BiologicalSex.FEMALE, 175.0, 75.0, ActivityLevel.SEDENTARY)
        assertEquals(166.0, male.bmrKcal - female.bmrKcal, 0.0)
    }
}
