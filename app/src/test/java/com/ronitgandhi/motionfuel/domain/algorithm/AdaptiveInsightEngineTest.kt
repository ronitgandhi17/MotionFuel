package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.DailyContext
import com.ronitgandhi.motionfuel.domain.model.InsightCategory
import com.ronitgandhi.motionfuel.domain.model.LocationQuality
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveInsightEngineTest {
    @Test
    fun idleDashboardDoesNotReportPoorGps() {
        val insights = AdaptiveInsightEngine().evaluate(
            DailyContext(
                workoutActive = false,
                gpsQuality = LocationQuality.POOR,
            ),
        )

        assertTrue(insights.none { it.category == InsightCategory.LOCATION_QUALITY_LOW })
    }

    @Test
    fun heatAndPaceDeclineProducesExplainableInsight() {
        val insights = AdaptiveInsightEngine().evaluate(
            DailyContext(
                workoutActive = true,
                paceDeclinePercent = 12.0,
                effortScore = 0.78,
                weather = WeatherContext(31.0, 55, 12.0, false),
            ),
        )

        val heatInsight = insights.first { it.category == InsightCategory.PACE_DECLINE_WITH_HEAT }
        assertEquals(3, heatInsight.evidence.size)
        assertTrue(heatInsight.confidence >= 0.8f)
    }

    @Test
    fun engineEmitsAtMostTwoRankedInsights() {
        val insights = AdaptiveInsightEngine().evaluate(
            DailyContext(
                workoutActive = true,
                paceDeclinePercent = 15.0,
                effortScore = 0.9,
                elevationGainMeters = 100.0,
                workoutDurationMinutes = 55.0,
                activeMinutesToday = 90.0,
                proteinLoggedG = 20.0,
                caloriesLoggedKcal = 600.0,
                weather = WeatherContext(33.0, 60, 8.0, false),
                rejectedGpsPoints = 2,
            ),
        )

        assertEquals(2, insights.size)
    }
}
