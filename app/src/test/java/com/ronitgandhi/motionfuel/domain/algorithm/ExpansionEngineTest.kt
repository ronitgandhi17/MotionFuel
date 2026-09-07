package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.SavedFood
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionEngineTest {
    @Test fun mealRecommendationsPreferProteinWhenProteinIsMissing() {
        val foods = listOf(food("Protein bowl", 400.0, 40.0), food("Toast", 400.0, 5.0))
        assertEquals("Protein bowl", ExpansionEngine.recommendMeals(foods, NutritionTotals(caloriesKcal = 900.0, proteinG = 10.0), 2200).first().name)
    }

    @Test fun labelParserAcceptsCommonNutritionNames() {
        val result = ExpansionEngine.parseNutritionLabel("Energy 245 kcal Total Fat 8.5 g Carbohydrate 31 g Protein 12 g")
        assertEquals(245.0, result.caloriesKcal, .01); assertEquals(12.0, result.proteinG, .01); assertEquals(31.0, result.carbohydratesG, .01); assertEquals(8.5, result.fatG, .01)
    }

    @Test fun loopRouteStartsAndEndsAtOrigin() {
        val origin = GeoPoint(-37.8, 144.9, timestampMillis = 1)
        val route = ExpansionEngine.generateLoopRoute(origin, 5_000.0, 10)
        assertEquals(5, route.size); assertEquals(route.first().latitude, route.last().latitude, 0.0); assertEquals(route.first().longitude, route.last().longitude, 0.0)
    }

    @Test fun zonesRejectInvalidPaces() {
        val zones = ExpansionEngine.paceZones(listOf(Double.NaN, 300.0, 400.0, 600.0))
        assertEquals(3, zones.size); assertEquals(3L, zones.sumOf { it.seconds })
    }

    @Test fun predictionRequiresDirectionTowardGoal() {
        val day = 86_400_000L
        val prediction = ExpansionEngine.predictWeight(listOf(WeightEntry("a", 80.0, day), WeightEntry("b", 79.0, day * 8)), 75.0)
        assertNotNull(prediction.projectedDateMillis); assertTrue(prediction.summary.contains("days"))
        assertEquals(null, ExpansionEngine.predictWeight(listOf(WeightEntry("a", 80.0, day), WeightEntry("b", 81.0, day * 8)), 75.0).projectedDateMillis)
    }

    @Test fun heartRateZonesAreOrderedAndBounded() {
        val zones = ExpansionEngine.heartRateZoneRanges(24)
        assertEquals(5, zones.size); assertTrue(zones.first().startsWith("Z1")); assertTrue(zones.last().startsWith("Z5"))
    }

    private fun food(name: String, calories: Double, protein: Double) = SavedFood(name, name, calories, protein, 30.0, 10.0, null, 1)
}
