package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import kotlin.math.abs

/** Estimates for recreational training; never an injury prediction. */
object TrainingAnalysis {
    data class Elevation(val ascent: Double, val descent: Double, val samples: List<Double>)
    fun elevation(points: List<GeoPoint>): Elevation {
        val samples = points.mapNotNull { it.altitudeMeters?.takeIf(Double::isFinite) }
        var anchor = samples.firstOrNull() ?: return Elevation(0.0, 0.0, emptyList())
        var up = 0.0; var down = 0.0
        for (altitude in samples.drop(1)) {
            val delta = altitude - anchor
            // A 3 m dead band suppresses small vertical GPS fluctuations.
            if (abs(delta) >= 3.0) {
                if (delta > 0) up += delta else down -= delta
                anchor = altitude
            }
        }
        return Elevation(up, down, samples)
    }
    fun load(workouts: List<WorkoutSummary>, now: Long): String {
        val day = 86_400_000L
        val recent = workouts.filter { it.startedAtMillis in (now - 28 * day)..now }
        fun effort(w: WorkoutSummary) = w.durationSeconds.coerceAtLeast(0) / 60.0 * if (w.type.name == "RUN") 2.0 else 1.0
        val week = recent.filter { it.startedAtMillis >= now - 7 * day }.sumOf(::effort)
        val baseline = recent.filter { it.startedAtMillis < now - 7 * day }.sumOf(::effort) / 3
        val trend = when {
            baseline <= 0 -> "Build at least four weeks of history to compare training load."
            week > baseline * 1.25 -> "Your recent load is above your previous weekly average. Consider an easier session if you feel tired."
            else -> "Recent load is within your usual range. Use how you feel to guide recovery."
        }
        return "Last 7 days: ${week.toInt()} effort minutes\nPrevious 3-week average: ${baseline.toInt()}\n$trend\nEstimated from duration and activity type; not a medical assessment."
    }
    fun locationInterval(batteryPercent: Int, stationary: Boolean, adaptive: Boolean): Long = when {
        !adaptive -> 1_000L
        batteryPercent in 0..15 -> 5_000L
        stationary -> 3_000L
        else -> 1_000L
    }
}

/** Uses reliable speed samples and hysteresis; manual pauses are handled by the service. */
class AutoPauseDetector {
    private var stoppedSince: Long? = null
    fun shouldPause(speed: Double, accuracy: Float, elapsed: Long): Boolean {
        if (!speed.isFinite() || !accuracy.isFinite() || accuracy > 25 || accuracy <= 0) {
            stoppedSince = null
            return false
        }
        if (speed >= 0.5) { stoppedSince = null; return false }
        if (stoppedSince == null) stoppedSince = elapsed
        return elapsed - requireNotNull(stoppedSince) >= 10_000
    }
    fun reset() { stoppedSince = null }
}
