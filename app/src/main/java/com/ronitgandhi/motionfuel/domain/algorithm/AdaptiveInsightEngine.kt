package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.DailyContext
import com.ronitgandhi.motionfuel.domain.model.Insight
import com.ronitgandhi.motionfuel.domain.model.InsightCategory
import com.ronitgandhi.motionfuel.domain.model.InsightEvidence
import com.ronitgandhi.motionfuel.domain.model.InsightPriority

class AdaptiveInsightEngine {
    fun evaluate(context: DailyContext): List<Insight> {
        val candidates = buildList {
            val heat = context.weather?.temperatureC ?: 0.0
            if (context.workoutActive && heat >= 28.0 && context.paceDeclinePercent >= 8.0 && context.effortScore >= 0.65) add(
                insight(
                    InsightCategory.PACE_DECLINE_WITH_HEAT,
                    "Effort appears higher",
                    "Your pace has eased while temperature and estimated effort are elevated.",
                    InsightPriority.HIGH,
                    0.88f,
                    "Pace trend" to "${context.paceDeclinePercent.toInt()}% slower",
                    "Temperature" to "${heat.toInt()}°C",
                    "Effort score" to "${(context.effortScore * 100).toInt()}/100",
                ),
            )
            if (context.elevationGainMeters > context.elevationBaselineMeters * 1.35 && context.elevationGainMeters > 25) add(
                insight(
                    InsightCategory.HIGH_ELEVATION_LOAD,
                    "Hillier than your baseline",
                    "Climbing load is higher than on your recent activities, so distance alone understates effort.",
                    InsightPriority.MEDIUM,
                    0.84f,
                    "Elevation gain" to "${context.elevationGainMeters.toInt()} m",
                    "Recent baseline" to "${context.elevationBaselineMeters.toInt()} m",
                ),
            )
            if (context.workoutDurationMinutes > context.durationBaselineMinutes * 1.30 && context.workoutDurationMinutes > 25) add(
                insight(
                    InsightCategory.LONGER_THAN_USUAL,
                    "Longer session than usual",
                    "This activity has passed your recent duration baseline. Keep the finish comfortable.",
                    InsightPriority.MEDIUM,
                    0.80f,
                    "Duration" to "${context.workoutDurationMinutes.toInt()} min",
                    "Recent baseline" to "${context.durationBaselineMinutes.toInt()} min",
                ),
            )
            val unusuallyActive = context.activeMinutesToday > context.activityBaselineMinutes * 1.30
            if (unusuallyActive && context.proteinTargetG > 0 && context.proteinLoggedG / context.proteinTargetG < 0.55) add(
                insight(
                    InsightCategory.RECOVERY_PROTEIN_CONTEXT,
                    "Review your next meal",
                    "Activity is above your usual level while logged protein remains below your target.",
                    InsightPriority.MEDIUM,
                    0.78f,
                    "Active minutes" to "${context.activeMinutesToday.toInt()} min",
                    "Protein" to "${context.proteinLoggedG.toInt()} / ${context.proteinTargetG.toInt()} g",
                ),
            )
            if (unusuallyActive && context.calorieTargetKcal > 0 && context.caloriesLoggedKcal / context.calorieTargetKcal < 0.45) add(
                insight(
                    InsightCategory.ENERGY_CONTEXT,
                    "Activity and intake are out of rhythm",
                    "You have logged more movement than usual and relatively little food so far today.",
                    InsightPriority.MEDIUM,
                    0.73f,
                    "Energy logged" to "${context.caloriesLoggedKcal.toInt()} kcal",
                    "Daily target" to "${context.calorieTargetKcal.toInt()} kcal",
                ),
            )
            if (context.activeDaysThisWeek >= context.activeDaysPreviousWeek + 2 && context.activeDaysThisWeek >= 3) add(
                insight(
                    InsightCategory.GOAL_PROGRESS,
                    "Consistency is building",
                    "You have recorded more active days this week than last week.",
                    InsightPriority.LOW,
                    0.90f,
                    "This week" to "${context.activeDaysThisWeek} active days",
                    "Last week" to "${context.activeDaysPreviousWeek} active days",
                ),
            )
            if (context.rejectedGpsPoints > 0) add(
                insight(
                    InsightCategory.GPS_DRIFT_CORRECTED,
                    "Route quality protected",
                    "MotionFuel removed location samples that did not agree with movement evidence.",
                    InsightPriority.LOW,
                    0.94f,
                    "Rejected samples" to context.rejectedGpsPoints.toString(),
                ),
            )
        }

        return candidates
            .sortedWith(compareByDescending<Insight> { it.priority.ordinal }.thenByDescending { it.confidence })
            .take(2)
    }

    private fun insight(
        category: InsightCategory,
        title: String,
        message: String,
        priority: InsightPriority,
        confidence: Float,
        vararg evidence: Pair<String, String>,
    ) = Insight(
        id = category.name,
        category = category,
        title = title,
        message = message,
        priority = priority,
        confidence = confidence,
        evidence = evidence.map { InsightEvidence(it.first, it.second) },
    )
}
