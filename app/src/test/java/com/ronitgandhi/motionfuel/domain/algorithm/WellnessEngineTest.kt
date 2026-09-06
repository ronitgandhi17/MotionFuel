package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.ConnectedHealthSnapshot
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessEngineTest {
    @Test
    fun adaptiveFuelAdjustmentIsBoundedToFifteenPercent() {
        val target = WellnessEngine.adaptiveTarget(
            baselineKcal = 2_000,
            exerciseCaloriesKcal = 5_000.0,
            steps = 50_000,
            weather = WeatherContext(35.0, 90, 4.0, false),
        )
        assertEquals(300, target.adjustmentKcal)
        assertEquals(2_300, target.recommendedKcal)
        assertTrue(target.hydrationTargetMl in 1_500..5_000)
    }

    @Test
    fun adaptiveFuelSanitizesNegativeActivity() {
        val target = WellnessEngine.adaptiveTarget(2_000, -500.0, -5, null)
        assertEquals(0, target.adjustmentKcal)
        assertEquals(2_000, target.recommendedKcal)
    }

    @Test
    fun hotHumidWeatherRaisesHydrationTarget() {
        val normal = WellnessEngine.adaptiveTarget(2_000, 200.0, 8_000, WeatherContext(20.0, 40, 3.0, false))
        val hot = WellnessEngine.adaptiveTarget(2_000, 200.0, 8_000, WeatherContext(32.0, 80, 3.0, false))
        assertEquals(600, hot.hydrationTargetMl - normal.hydrationTargetMl)
    }

    @Test
    fun recoveryRespondsToSleepHeartRateAndLoad() {
        val now = 1_800_000_000_000L
        val rested = WellnessEngine.recovery(ConnectedHealthSnapshot(sleepHours = 8.5, restingHeartRateBpm = 55), emptyList(), now)
        val fatigued = WellnessEngine.recovery(
            ConnectedHealthSnapshot(sleepHours = 4.5, restingHeartRateBpm = 95),
            listOf(workout(now - 1_000, duration = 8_000)),
            now,
        )
        assertTrue(rested.score > fatigued.score)
        assertEquals("Recover", fatigued.label)
    }

    @Test
    fun personalRecordsIgnoreInvalidPaces() {
        val now = System.currentTimeMillis()
        val records = WellnessEngine.personalRecords(
            listOf(workout(now, distance = 5_000.0, pace = 320.0), workout(now - 86_400_000L, distance = 3_000.0, pace = Double.NaN)),
            now,
        )
        assertEquals(5_000.0, records.longestDistanceMeters, 0.001)
        assertEquals(320.0, requireNotNull(records.fastestPaceSecPerKm), 0.001)
        assertTrue(records.currentWorkoutStreakDays >= 1)
    }

    @Test
    fun kilometreSplitsAreCalculatedFromRouteTimestamps() {
        val route = listOf(
            GeoPoint(0.0, 0.0, 10.0, timestampMillis = 0),
            GeoPoint(0.0, 0.0091, 15.0, timestampMillis = 300_000),
            GeoPoint(0.0, 0.0182, 20.0, timestampMillis = 620_000),
        )
        val splits = WellnessEngine.splits(workout(0, distance = 2_020.0, route = route))
        assertEquals(2, splits.size)
        assertTrue(splits.all { it.durationSeconds > 0 })
    }

    @Test
    fun weeklyReportUsesOnlyRecentDataAndProvidesFocus() {
        val now = 1_800_000_000_000L
        val meals = listOf(
            NutritionEntry("n", "Meal", 600.0, 30.0, 60.0, 20.0, MealType.DINNER, now - 1_000),
            NutritionEntry("old", "Old", 9_000.0, 1.0, 1.0, 1.0, MealType.DINNER, now - 9 * 86_400_000L),
        )
        val report = WellnessEngine.weeklyReport(
            listOf(workout(now - 1_000)),
            meals,
            listOf(WeightEntry("a", 80.0, now - 4_000), WeightEntry("b", 79.5, now - 1_000)),
            workoutTarget = 3,
            nowMillis = now,
        )
        assertEquals(1, report.workouts)
        assertEquals(600.0, report.averageFoodCaloriesKcal, 0.001)
        assertEquals(-0.5, requireNotNull(report.weightChangeKg), 0.001)
        assertTrue(report.focus.isNotBlank())
    }

    private fun workout(
        startedAt: Long,
        duration: Long = 1_800,
        distance: Double = 3_000.0,
        pace: Double? = 360.0,
        route: List<GeoPoint> = emptyList(),
    ) = WorkoutSummary(
        id = startedAt.toString(), type = WorkoutType.RUN, startedAtMillis = startedAt,
        durationSeconds = duration, distanceMeters = distance, averagePaceSecPerKm = pace,
        steps = 4_000, elevationGainMeters = 20.0, caloriesKcal = 250.0,
        dominantActivity = ActivityType.RUNNING, rejectedGpsPoints = 0, route = route,
    )
}
