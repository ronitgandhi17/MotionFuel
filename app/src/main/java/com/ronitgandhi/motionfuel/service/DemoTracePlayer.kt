package com.ronitgandhi.motionfuel.service

import com.ronitgandhi.motionfuel.domain.algorithm.ActivityStateStabilizer
import com.ronitgandhi.motionfuel.domain.algorithm.EnergyEstimator
import com.ronitgandhi.motionfuel.domain.algorithm.GpsFilter
import com.ronitgandhi.motionfuel.domain.algorithm.SensorFusionClassifier
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.LocationQuality
import com.ronitgandhi.motionfuel.domain.model.SensorFeatureWindow
import com.ronitgandhi.motionfuel.domain.model.WorkoutStatus
import com.ronitgandhi.motionfuel.domain.model.WorkoutTelemetry
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DemoTracePlayer {
    private val filter = GpsFilter()
    private val classifier = SensorFusionClassifier()
    private val stabilizer = ActivityStateStabilizer(2)
    private var job: Job? = null
    private var paused = false

    fun start(scope: CoroutineScope, type: WorkoutType, weightKg: Double): Job {
        stop(reset = true)
        paused = false
        filter.reset()
        stabilizer.reset()
        WorkoutSessionController.publish(WorkoutTelemetry(status = WorkoutStatus.ACTIVE, type = type, isDemo = true))
        return scope.launch {
            var index = 0
            val start = System.currentTimeMillis()
            while (isActive && index < 90) {
                delay(550)
                if (paused) continue
                index++
                val simulatedSeconds = index * 5L
                val features = featuresFor(index)
                val activity = stabilizer.update(classifier.classify(features))
                val raw = pointFor(index, start + simulatedSeconds * 1_000)
                val filtered = filter.evaluate(raw, activity.type)
                WorkoutSessionController.update { current ->
                    val route = if (filtered.accepted && filtered.point != null) current.route + filtered.point else current.route
                    val distance = current.distanceMeters + filtered.distanceDeltaMeters
                    val averagePace = if (distance > 20) simulatedSeconds / (distance / 1_000.0) else null
                    val speed = features.gpsSpeedMps ?: 0.0
                    current.copy(
                        elapsedSeconds = simulatedSeconds,
                        distanceMeters = distance,
                        currentPaceSecPerKm = if (speed > 0.4) 1_000.0 / speed else null,
                        averagePaceSecPerKm = averagePace,
                        steps = (index * when (activity.type) { ActivityType.RUNNING -> 14; ActivityType.WALKING -> 9; else -> 1 }).toLong(),
                        cadenceSpm = features.stepRatePerMinute?.toInt() ?: 0,
                        elevationGainMeters = route.zipWithNext().sumOf { pair ->
                            ((pair.second.altitudeMeters ?: 0.0) - (pair.first.altitudeMeters ?: 0.0)).coerceAtLeast(0.0)
                        },
                        caloriesKcal = EnergyEstimator.calories(weightKg, simulatedSeconds, activity.type),
                        activity = activity,
                        gpsQuality = filtered.quality,
                        route = route,
                        rejectedGpsPoints = current.rejectedGpsPoints + if (filtered.accepted) 0 else 1,
                    )
                }
            }
        }.also { job = it }
    }

    fun pause() {
        paused = true
        WorkoutSessionController.update { it.copy(status = WorkoutStatus.PAUSED) }
    }

    fun resume() {
        paused = false
        WorkoutSessionController.update { it.copy(status = WorkoutStatus.ACTIVE) }
    }

    fun finish() {
        job?.cancel()
        WorkoutSessionController.update { it.copy(status = WorkoutStatus.COMPLETE) }
    }

    fun stop(reset: Boolean = false) {
        job?.cancel()
        job = null
        if (reset) WorkoutSessionController.reset()
    }

    private fun featuresFor(index: Int): SensorFeatureWindow = when {
        index <= 6 -> SensorFeatureWindow(0.06, 0.02, 0.004, 0.0, 0.05, 7f)
        index <= 20 -> SensorFeatureWindow(0.72, 0.48, 0.09, 102.0, 1.35, 8f)
        index <= 62 -> SensorFeatureWindow(2.25, 1.45, 0.34, 168.0, 3.0, 9f)
        else -> SensorFeatureWindow(1.65, 1.12, 0.25, 154.0, 2.35, 13f)
    }

    private fun pointFor(index: Int, timestamp: Long): GeoPoint {
        val baseLat = -37.8136
        val baseLon = 144.9631
        if (index == 34) {
            return GeoPoint(baseLat + 0.012, baseLon + 0.012, 48.0, 7f, timestamp)
        }
        val (east, north) = when {
            index <= 6 -> 0.0 to 0.0
            index <= 20 -> {
                val walking = index - 6
                walking * 0.00007 to walking * 0.000025
            }
            index <= 62 -> {
                val running = index - 20
                (14 * 0.00007 + running * 0.00014) to (14 * 0.000025 + running * 0.00005)
            }
            else -> {
                val easing = index - 62
                (14 * 0.00007 + 42 * 0.00014 + easing * 0.00010) to
                    (14 * 0.000025 + 42 * 0.00005 + easing * 0.000035)
            }
        }
        val altitude = 18.0 + when {
            index in 40..58 -> (index - 40) * 3.2
            index > 58 -> 57.6 - (index - 58) * 0.5
            else -> 0.0
        }
        return GeoPoint(baseLat + north, baseLon + east, altitude, if (index in 70..73) 32f else 8f, timestamp)
    }
}
