package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityClassification
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.SensorFeatureWindow
import kotlin.math.max

class SensorFusionClassifier {
    fun classify(window: SensorFeatureWindow): ActivityClassification {
        val stepRate = window.stepRatePerMinute.finiteOrZero()
        val speed = window.gpsSpeedMps.finiteOrZero()
        val energy = window.accelerationEnergy.finiteOrZero()
        val gyro = window.gyroscopeVariance.finiteOrZero()

        val stationary = mean(
            inverseScore(speed, 0.25, 1.0),
            inverseScore(stepRate, 8.0, 45.0),
            inverseScore(energy, 0.12, 0.75),
        )
        val walking = mean(
            bandScore(speed, 0.45, 1.45, 2.25),
            bandScore(stepRate, 45.0, 105.0, 145.0),
            bandScore(energy, 0.2, 0.75, 1.8),
            bandScore(gyro, 0.01, 0.10, 0.45),
        )
        val running = mean(
            risingScore(speed, 1.8, 3.0),
            risingScore(stepRate, 120.0, 170.0),
            risingScore(energy, 1.0, 2.6),
            risingScore(gyro, 0.12, 0.45),
        )

        val scores = mapOf(
            ActivityType.STATIONARY to stationary,
            ActivityType.WALKING to walking,
            ActivityType.RUNNING to running,
        )
        val winner = scores.maxBy { it.value }
        val sorted = scores.values.sortedDescending()
        val separation = max(0.0, sorted[0] - sorted[1])
        val confidence = (winner.value * 0.78 + separation * 0.22).coerceIn(0.0, 1.0)
        val type = if (confidence < 0.42) ActivityType.UNKNOWN else winner.key

        val evidence = buildList {
            window.gpsSpeedMps?.let { add("GPS %.1f m/s".format(it)) }
            window.stepRatePerMinute?.let { add("%.0f steps/min".format(it)) }
            add("Motion energy %.2f".format(energy))
            if (window.gyroscopeVariance > 0.0) add("Rotation variance %.2f".format(gyro))
        }
        return ActivityClassification(type, confidence.toFloat(), evidence)
    }

    private fun Double?.finiteOrZero() = this?.takeIf(Double::isFinite) ?: 0.0

    private fun mean(vararg values: Double) = values.average()

    private fun risingScore(value: Double, low: Double, high: Double) =
        ((value - low) / (high - low)).coerceIn(0.0, 1.0)

    private fun inverseScore(value: Double, low: Double, high: Double) =
        (1.0 - risingScore(value, low, high)).coerceIn(0.0, 1.0)

    private fun bandScore(value: Double, low: Double, peak: Double, high: Double): Double = when {
        value <= low || value >= high -> 0.0
        value <= peak -> (value - low) / (peak - low)
        else -> (high - value) / (high - peak)
    }
}

class ActivityStateStabilizer(private val requiredConsecutiveWindows: Int = 2) {
    private var current = ActivityType.UNKNOWN
    private var candidate = ActivityType.UNKNOWN
    private var candidateCount = 0

    fun update(classification: ActivityClassification): ActivityClassification {
        if (classification.type == ActivityType.UNKNOWN || classification.confidence < 0.45f) {
            return classification.copy(type = current)
        }
        if (classification.type == current) {
            candidate = current
            candidateCount = 0
            return classification
        }
        if (classification.type == candidate) candidateCount++ else {
            candidate = classification.type
            candidateCount = 1
        }
        if (candidateCount >= requiredConsecutiveWindows) {
            current = candidate
            candidateCount = 0
        }
        return classification.copy(type = current)
    }

    fun reset() {
        current = ActivityType.UNKNOWN
        candidate = ActivityType.UNKNOWN
        candidateCount = 0
    }
}
