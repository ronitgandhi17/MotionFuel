package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityClassification
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.DailyContext
import com.ronitgandhi.motionfuel.domain.model.InsightCategory
import com.ronitgandhi.motionfuel.domain.model.SensorFeatureWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorAndInsightBoundaryTest {
    private val classifier = SensorFusionClassifier()

    @Test
    fun zeroMovementClassifiesAsStationary() {
        val result = classifier.classify(SensorFeatureWindow(0.0, 0.0, 0.0, 0.0, 0.0, 5f))
        assertEquals(ActivityType.STATIONARY, result.type)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun representativeWalkingSignalsClassifyAsWalking() {
        val result = classifier.classify(SensorFeatureWindow(0.75, 0.2, 0.10, 100.0, 1.4, 8f))
        assertEquals(ActivityType.WALKING, result.type)
    }

    @Test
    fun confidenceStaysBoundedAcrossSignalGrid() {
        val values = listOf(0.0, 0.1, 0.5, 1.0, 2.0, 5.0, 50.0, 200.0)
        values.forEach { energy ->
            values.forEach { speed ->
                val result = classifier.classify(SensorFeatureWindow(energy, energy / 2, energy / 4, speed * 30, speed, 10f))
                assertTrue(result.confidence.isFinite())
                assertTrue(result.confidence in 0f..1f)
            }
        }
    }

    @Test
    fun stabilizerRequiresConsecutiveCandidateWindowsAndCanReset() {
        val stabilizer = ActivityStateStabilizer(2)
        val running = ActivityClassification(ActivityType.RUNNING, 0.9f, emptyList())
        assertEquals(ActivityType.UNKNOWN, stabilizer.update(running).type)
        assertEquals(ActivityType.RUNNING, stabilizer.update(running).type)
        stabilizer.reset()
        assertEquals(ActivityType.UNKNOWN, stabilizer.update(running).type)
    }

    @Test
    fun lowConfidenceUnknownDoesNotReplaceStableActivity() {
        val stabilizer = ActivityStateStabilizer(1)
        stabilizer.update(ActivityClassification(ActivityType.WALKING, 0.9f, emptyList()))
        val result = stabilizer.update(ActivityClassification(ActivityType.UNKNOWN, 0.1f, emptyList()))
        assertEquals(ActivityType.WALKING, result.type)
    }

    @Test
    fun emptyDailyContextProducesNoInsights() {
        assertTrue(AdaptiveInsightEngine().evaluate(DailyContext()).isEmpty())
    }

    @Test
    fun elevationRuleIncludesMeasuredAndBaselineEvidence() {
        val insights = AdaptiveInsightEngine().evaluate(DailyContext(elevationGainMeters = 100.0, elevationBaselineMeters = 30.0))
        val elevation = insights.single { it.category == InsightCategory.HIGH_ELEVATION_LOAD }
        assertEquals(2, elevation.evidence.size)
    }

    @Test
    fun consistencyRuleTriggersOnlyWithRequiredImprovement() {
        val engine = AdaptiveInsightEngine()
        assertTrue(engine.evaluate(DailyContext(activeDaysThisWeek = 2, activeDaysPreviousWeek = 0)).isEmpty())
        assertEquals(
            InsightCategory.GOAL_PROGRESS,
            engine.evaluate(DailyContext(activeDaysThisWeek = 3, activeDaysPreviousWeek = 1)).single().category,
        )
    }

    @Test
    fun energyEstimatorMatchesMetFormulaAndZeroDuration() {
        assertEquals(0.0, EnergyEstimator.calories(75.0, 0, ActivityType.RUNNING), 0.0)
        assertEquals(8.3 * 3.5 * 75.0 / 200.0 * 30.0, EnergyEstimator.calories(75.0, 1_800, ActivityType.RUNNING), 0.000001)
    }
}
