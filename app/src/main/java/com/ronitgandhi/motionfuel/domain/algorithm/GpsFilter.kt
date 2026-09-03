package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.FilteredLocation
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.LocationQuality
import kotlin.math.max

class GpsFilter(
    private val maximumAccuracyMeters: Float = 40f,
    private val maximumPlausibleSpeedMps: Double = 12.5,
    private val smoothingAlpha: Double = 0.35,
) {
    private var previousAccepted: GeoPoint? = null

    fun reset() {
        previousAccepted = null
    }

    fun evaluate(sample: GeoPoint, movement: ActivityType): FilteredLocation {
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite() ||
            !sample.accuracyMeters.isFinite() || sample.altitudeMeters?.isFinite() == false
        ) {
            return rejected(sample, LocationQuality.POOR, "Location sample contains a non-finite value")
        }
        val quality = when {
            sample.accuracyMeters <= 12f -> LocationQuality.GOOD
            sample.accuracyMeters <= maximumAccuracyMeters -> LocationQuality.FAIR
            else -> LocationQuality.POOR
        }

        if (sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0) {
            return rejected(sample, quality, "Coordinates outside valid range")
        }
        if (sample.accuracyMeters <= 0f || sample.accuracyMeters > maximumAccuracyMeters) {
            return rejected(sample, quality, "Accuracy is ${sample.accuracyMeters.toInt()} m")
        }

        val previous = previousAccepted
        if (previous == null) {
            previousAccepted = sample
            return FilteredLocation(sample, sample, true, quality)
        }

        val elapsedSeconds = (sample.timestampMillis - previous.timestampMillis) / 1_000.0
        if (elapsedSeconds <= 0.0) return rejected(sample, quality, "Non-increasing timestamp")

        val rawDistance = GeoMath.distanceMeters(previous, sample)
        val impliedSpeed = rawDistance / elapsedSeconds
        if (impliedSpeed > maximumPlausibleSpeedMps) {
            return rejected(sample, quality, "Impossible jump (${impliedSpeed.toInt()} m/s)")
        }

        val driftRadius = max(8.0, sample.accuracyMeters.toDouble() * 0.65)
        if (movement == ActivityType.STATIONARY && rawDistance < driftRadius) {
            return rejected(sample, quality, "Stationary GPS drift")
        }

        val smoothed = sample.copy(
            latitude = previous.latitude + smoothingAlpha * (sample.latitude - previous.latitude),
            longitude = previous.longitude + smoothingAlpha * (sample.longitude - previous.longitude),
            altitudeMeters = when {
                sample.altitudeMeters == null -> previous.altitudeMeters
                previous.altitudeMeters == null -> sample.altitudeMeters
                else -> previous.altitudeMeters + smoothingAlpha * (sample.altitudeMeters - previous.altitudeMeters)
            },
        )
        val distanceDelta = GeoMath.distanceMeters(previous, smoothed)
        previousAccepted = smoothed
        return FilteredLocation(sample, smoothed, true, quality, distanceDelta)
    }

    private fun rejected(sample: GeoPoint, quality: LocationQuality, reason: String) =
        FilteredLocation(sample, null, false, quality, rejectionReason = reason)
}
