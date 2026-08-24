package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.GeoPoint

data class PrivacyZone(val centre: GeoPoint, val radiusMeters: Double)

object PrivacyZoneMasker {
    fun mask(route: List<GeoPoint>, zones: List<PrivacyZone>): List<GeoPoint> {
        if (zones.isEmpty()) return route
        return route.filter { point ->
            zones.none { zone -> GeoMath.distanceMeters(point, zone.centre) < zone.radiusMeters }
        }
    }
}

object EnergyEstimator {
    fun calories(weightKg: Double, durationSeconds: Long, activity: com.ronitgandhi.motionfuel.domain.model.ActivityType): Double {
        val met = when (activity) {
            com.ronitgandhi.motionfuel.domain.model.ActivityType.RUNNING -> 8.3
            com.ronitgandhi.motionfuel.domain.model.ActivityType.WALKING -> 3.5
            else -> 1.8
        }
        return met * 3.5 * weightKg / 200.0 * (durationSeconds / 60.0)
    }
}
