package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ExerciseZone
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.MealRecommendation
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.ProgressPrediction
import com.ronitgandhi.motionfuel.domain.model.SavedFood
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.max

object ExpansionEngine {
    fun recommendMeals(
        foods: List<SavedFood>,
        consumed: NutritionTotals,
        calorieGoal: Int,
        proteinGoalG: Double = max(60.0, calorieGoal * 0.075),
        limit: Int = 3,
    ): List<MealRecommendation> {
        val caloriesLeft = (calorieGoal - consumed.caloriesKcal).coerceAtLeast(0.0)
        val proteinLeft = (proteinGoalG - consumed.proteinG).coerceAtLeast(0.0)
        return foods.filter { it.caloriesKcal > 0 && it.caloriesKcal <= caloriesLeft * 1.15 + 50 }
            .map { food ->
                val calorieFit = 1.0 - kotlin.math.min(1.0, kotlin.math.abs(caloriesLeft * .35 - food.caloriesKcal) / max(1.0, caloriesLeft))
                val proteinFit = kotlin.math.min(1.0, food.proteinG / max(1.0, proteinLeft * .35))
                MealRecommendation(food.id, food.name, if (proteinFit >= .7) "Helps close your protein gap" else "Fits today's remaining energy", food.caloriesKcal, food.proteinG, calorieFit * .55 + proteinFit * .45)
            }.sortedByDescending { it.score }.take(limit)
    }

    fun parseNutritionLabel(text: String): NutritionTotals {
        fun value(vararg labels: String): Double {
            val alternation = labels.joinToString("|") { Regex.escape(it) }
            return Regex("(?i)(?:$alternation)\\s*[: ]?\\s*(\\d+(?:[.,]\\d+)?)")
                .find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        }
        return NutritionTotals(
            caloriesKcal = value("calories", "energy"),
            proteinG = value("protein"),
            carbohydratesG = value("carbohydrate", "carbohydrates", "carbs"),
            fatG = value("total fat", "fat"),
        )
    }

    fun generateLoopRoute(origin: GeoPoint, distanceMeters: Double, now: Long = System.currentTimeMillis()): List<GeoPoint> {
        val side = (distanceMeters.coerceIn(500.0, 42_000.0) / 4.0)
        val latDelta = side / 111_320.0
        val lonDelta = side / (111_320.0 * cos(Math.toRadians(origin.latitude)).coerceAtLeast(.2))
        return listOf(
            origin.copy(timestampMillis = now),
            origin.copy(latitude = origin.latitude + latDelta, timestampMillis = now + 1),
            origin.copy(latitude = origin.latitude + latDelta, longitude = origin.longitude + lonDelta, timestampMillis = now + 2),
            origin.copy(longitude = origin.longitude + lonDelta, timestampMillis = now + 3),
            origin.copy(timestampMillis = now + 4),
        )
    }

    fun paceZones(paceSamples: List<Double>): List<ExerciseZone> {
        if (paceSamples.isEmpty()) return emptyList()
        val best = paceSamples.filter { it.isFinite() && it > 0 }.minOrNull() ?: return emptyList()
        val counts = LongArray(3)
        paceSamples.filter { it.isFinite() && it > 0 }.forEach { pace ->
            val index = when { pace <= best * 1.15 -> 2; pace <= best * 1.4 -> 1; else -> 0 }
            counts[index]++
        }
        return listOf(ExerciseZone("Easy", counts[0], 0), ExerciseZone("Steady", counts[1], 1), ExerciseZone("Fast", counts[2], 2))
    }

    fun heartRateZoneRanges(age: Int): List<String> {
        val maximum = (208 - 0.7 * age.coerceIn(13, 120)).toInt()
        return listOf(50 to 60, 60 to 70, 70 to 80, 80 to 90, 90 to 100).mapIndexed { index, range ->
            val low = (maximum * range.first / 100.0).toInt(); val high = (maximum * range.second / 100.0).toInt()
            "Z${index + 1} $low–$high bpm"
        }
    }

    fun predictWeight(history: List<WeightEntry>, targetKg: Double): ProgressPrediction {
        val ordered = history.sortedBy { it.recordedAtMillis }
        if (ordered.size < 2 || targetKg !in 30.0..350.0) return ProgressPrediction("Add at least two weight entries for a projection", null, "Insufficient data")
        val first = ordered.first(); val last = ordered.last()
        val days = ((last.recordedAtMillis - first.recordedAtMillis).toDouble() / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(1.0)
        val dailyChange = (last.weightKg - first.weightKg) / days
        val remaining = targetKg - last.weightKg
        if (dailyChange == 0.0 || remaining / dailyChange <= 0) return ProgressPrediction("Current trend does not move toward the selected goal", null, "Low")
        val projectedDays = (remaining / dailyChange).coerceIn(1.0, 730.0).toLong()
        return ProgressPrediction("At the recent trend, the goal may be reached in about $projectedDays days", last.recordedAtMillis + TimeUnit.DAYS.toMillis(projectedDays), if (ordered.size >= 7) "Moderate" else "Low")
    }
}
