package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.AdaptiveFuelTarget
import com.ronitgandhi.motionfuel.domain.model.ConnectedHealthSnapshot
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.PersonalRecords
import com.ronitgandhi.motionfuel.domain.model.RecoveryScore
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import com.ronitgandhi.motionfuel.domain.model.WeeklyReport
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.domain.model.WorkoutSplit
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import java.util.Calendar
import kotlin.math.roundToInt

object WellnessEngine {
    fun adaptiveTarget(
        baselineKcal: Int,
        exerciseCaloriesKcal: Double,
        steps: Long,
        weather: WeatherContext?,
        recentWeightChangeKg: Double? = null,
    ): AdaptiveFuelTarget {
        val reasons = mutableListOf<String>()
        var adjustment = (exerciseCaloriesKcal.coerceAtLeast(0.0) * 0.35).roundToInt()
        if (exerciseCaloriesKcal >= 250) reasons += "35% of estimated workout energy"
        val stepBonus = (((steps - 8_000).coerceAtLeast(0)) / 1_000 * 18).toInt().coerceAtMost(120)
        if (stepBonus > 0) reasons += "higher step count"
        adjustment += stepBonus
        val hot = weather?.temperatureC?.let { it >= 28 } == true
        val humid = weather?.humidityPercent?.let { it >= 70 } == true
        if (hot) reasons += "hot weather hydration"
        if (humid) reasons += "high humidity"
        recentWeightChangeKg?.let {
            if (it < -1.0) {
                adjustment += 80
                reasons += "rapid recent weight decrease"
            } else if (it > 1.0) {
                adjustment -= 60
                reasons += "rapid recent weight increase"
            }
        }
        val limit = (baselineKcal * 0.15).roundToInt()
        adjustment = adjustment.coerceIn(-limit, limit)
        val hydration = 2_300 + (exerciseCaloriesKcal * 0.7).roundToInt() + if (hot) 400 else 0 + if (humid) 200 else 0
        return AdaptiveFuelTarget(
            baselineKcal = baselineKcal,
            recommendedKcal = (baselineKcal + adjustment).coerceAtLeast(1_200),
            adjustmentKcal = adjustment,
            hydrationTargetMl = hydration.coerceIn(1_500, 5_000),
            reasons = reasons.ifEmpty { listOf("baseline profile and normal activity") },
        )
    }

    fun recovery(
        health: ConnectedHealthSnapshot,
        workouts: List<WorkoutSummary>,
        nowMillis: Long = System.currentTimeMillis(),
    ): RecoveryScore {
        var score = 75
        val factors = mutableListOf<String>()
        health.sleepHours?.let {
            when {
                it < 6 -> { score -= 25; factors += "short sleep" }
                it >= 8 -> { score += 10; factors += "strong sleep duration" }
                else -> factors += "moderate sleep duration"
            }
        } ?: factors.add("sleep data unavailable")
        health.restingHeartRateBpm?.let {
            when {
                it >= 85 -> { score -= 15; factors += "elevated resting heart rate" }
                it <= 60 -> { score += 5; factors += "low resting heart rate" }
                else -> factors += "normal resting heart rate"
            }
        }
        val recentLoad = workouts.filter { nowMillis - it.startedAtMillis in 0..172_800_000L }.sumOf { it.durationSeconds }
        if (recentLoad > 7_200) { score -= 15; factors += "high 48-hour workout load" }
        val bounded = score.coerceIn(0, 100)
        val (label, recommendation) = when {
            bounded >= 80 -> "Ready" to "A normal or challenging session is reasonable if you feel well."
            bounded >= 60 -> "Balanced" to "Choose a normal session and monitor how you feel."
            bounded >= 40 -> "Take it easy" to "Prefer walking, mobility or an easy recovery workout."
            else -> "Recover" to "Consider rest and prioritise sleep and hydration."
        }
        return RecoveryScore(bounded, label, recommendation, factors)
    }

    fun splits(workout: WorkoutSummary): List<WorkoutSplit> {
        val route = workout.route.sortedBy { it.timestampMillis }
        if (route.size < 2 || workout.distanceMeters < 1_000) return emptyList()
        val cumulative = DoubleArray(route.size)
        for (index in 1 until route.size) cumulative[index] = cumulative[index - 1] + GeoMath.distanceMeters(route[index - 1], route[index])
        val result = mutableListOf<WorkoutSplit>()
        var previousIndex = 0
        var kilometre = 1
        while (kilometre * 1_000.0 <= cumulative.last()) {
            val target = kilometre * 1_000.0
            val index = cumulative.indexOfFirst { it >= target }.coerceAtLeast(previousIndex + 1)
            val start = route[previousIndex]
            val end = route[index]
            val seconds = ((end.timestampMillis - start.timestampMillis) / 1_000L).coerceAtLeast(1)
            result += WorkoutSplit(
                kilometre = kilometre,
                durationSeconds = seconds,
                paceSecondsPerKm = seconds.toDouble(),
                elevationDeltaMeters = (end.altitudeMeters ?: 0.0) - (start.altitudeMeters ?: 0.0),
            )
            previousIndex = index
            kilometre++
        }
        return result
    }

    fun personalRecords(workouts: List<WorkoutSummary>, nowMillis: Long = System.currentTimeMillis()): PersonalRecords {
        val validPaces = workouts.mapNotNull { it.averagePaceSecPerKm }.filter { it.isFinite() && it > 0 }
        val activeDays = workouts.map { dayStart(it.startedAtMillis) }.distinct().toSet()
        var streak = 0
        var cursor = dayStart(nowMillis)
        if (cursor !in activeDays) cursor -= 86_400_000L
        while (cursor in activeDays) { streak++; cursor -= 86_400_000L }
        return PersonalRecords(
            longestDistanceMeters = workouts.maxOfOrNull { it.distanceMeters } ?: 0.0,
            longestDurationSeconds = workouts.maxOfOrNull { it.durationSeconds } ?: 0,
            fastestPaceSecPerKm = validPaces.minOrNull(),
            mostSteps = workouts.maxOfOrNull { it.steps } ?: 0,
            currentWorkoutStreakDays = streak,
        )
    }

    fun weeklyReport(
        workouts: List<WorkoutSummary>,
        nutrition: List<NutritionEntry>,
        weights: List<WeightEntry>,
        workoutTarget: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): WeeklyReport {
        val cutoff = nowMillis - 7 * 86_400_000L
        val recentWorkouts = workouts.filter { it.startedAtMillis >= cutoff }
        val recentNutrition = nutrition.filter { it.consumedAtMillis >= cutoff }
        val foodDays = recentNutrition.groupBy { dayStart(it.consumedAtMillis) }
        val recentWeights = weights.filter { it.recordedAtMillis >= cutoff }.sortedBy { it.recordedAtMillis }
        val activeDays = recentWorkouts.map { dayStart(it.startedAtMillis) }.distinct().size
        val nutritionDays = foodDays.size
        val consistency = ((activeDays.toDouble() / workoutTarget.coerceAtLeast(1) * 55) + (nutritionDays / 7.0 * 45)).roundToInt().coerceIn(0, 100)
        val focus = when {
            activeDays < workoutTarget -> "Schedule ${workoutTarget - activeDays} more active day${if (workoutTarget - activeDays == 1) "" else "s"}."
            nutritionDays < 5 -> "Log meals consistently to improve recommendations."
            else -> "Maintain your routine and prioritise recovery."
        }
        return WeeklyReport(
            workouts = recentWorkouts.size,
            activeMinutes = recentWorkouts.sumOf { it.durationSeconds } / 60,
            distanceMeters = recentWorkouts.sumOf { it.distanceMeters },
            exerciseCaloriesKcal = recentWorkouts.sumOf { it.caloriesKcal },
            averageFoodCaloriesKcal = foodDays.values.map { day -> day.sumOf { it.caloriesKcal } }.average().takeIf { !it.isNaN() } ?: 0.0,
            proteinAverageG = foodDays.values.map { day -> day.sumOf { it.proteinG } }.average().takeIf { !it.isNaN() } ?: 0.0,
            weightChangeKg = if (recentWeights.size >= 2) recentWeights.last().weightKg - recentWeights.first().weightKg else null,
            consistencyScore = consistency,
            focus = focus,
        )
    }

    private fun dayStart(timeMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
