package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsFilterTest {
    @Test
    fun impossibleJumpIsRejected() {
        val filter = GpsFilter()
        val first = GeoPoint(-37.8136, 144.9631, accuracyMeters = 7f, timestampMillis = 1_000)
        val jump = GeoPoint(-37.8036, 144.9731, accuracyMeters = 7f, timestampMillis = 6_000)

        assertTrue(filter.evaluate(first, ActivityType.RUNNING).accepted)
        val result = filter.evaluate(jump, ActivityType.RUNNING)

        assertFalse(result.accepted)
        assertTrue(result.rejectionReason.orEmpty().contains("Impossible jump"))
    }

    @Test
    fun plausibleMovementAddsDistance() {
        val filter = GpsFilter()
        val first = GeoPoint(-37.8136, 144.9631, accuracyMeters = 7f, timestampMillis = 1_000)
        val next = GeoPoint(-37.8135, 144.9632, accuracyMeters = 7f, timestampMillis = 6_000)
        filter.evaluate(first, ActivityType.WALKING)

        val result = filter.evaluate(next, ActivityType.WALKING)

        assertTrue(result.accepted)
        assertTrue(result.distanceDeltaMeters in 3.0..12.0)
    }
}
