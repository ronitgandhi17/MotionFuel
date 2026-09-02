package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.LocationQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsFilterBoundaryTest {
    private fun point(
        latitude: Double = -37.8136,
        longitude: Double = 144.9631,
        accuracy: Float = 7f,
        time: Long = 1_000,
    ) = GeoPoint(latitude, longitude, accuracyMeters = accuracy, timestampMillis = time)

    @Test
    fun invalidLatitudeAndLongitudeValuesAreRejected() {
        listOf(
            point(latitude = -90.01), point(latitude = 90.01), point(longitude = -180.01), point(longitude = 180.01),
            point(latitude = Double.NaN), point(longitude = Double.POSITIVE_INFINITY),
        ).forEach { assertFalse(GpsFilter().evaluate(it, ActivityType.WALKING).accepted) }
    }

    @Test
    fun invalidAccuracyValuesAreRejected() {
        listOf(-1f, 0f, 40.01f, Float.NaN, Float.POSITIVE_INFINITY).forEach { accuracy ->
            assertFalse(GpsFilter().evaluate(point(accuracy = accuracy), ActivityType.WALKING).accepted)
        }
    }

    @Test
    fun qualityThresholdsAreClassifiedCorrectly() {
        assertEquals(LocationQuality.GOOD, GpsFilter().evaluate(point(accuracy = 12f), ActivityType.WALKING).quality)
        assertEquals(LocationQuality.FAIR, GpsFilter().evaluate(point(accuracy = 12.01f), ActivityType.WALKING).quality)
        assertEquals(LocationQuality.FAIR, GpsFilter().evaluate(point(accuracy = 40f), ActivityType.WALKING).quality)
        assertEquals(LocationQuality.POOR, GpsFilter().evaluate(point(accuracy = 40.01f), ActivityType.WALKING).quality)
    }

    @Test
    fun duplicateAndDecreasingTimestampsAreRejected() {
        listOf(1_000L, 999L).forEach { secondTime ->
            val filter = GpsFilter()
            assertTrue(filter.evaluate(point(time = 1_000), ActivityType.WALKING).accepted)
            assertFalse(filter.evaluate(point(longitude = 144.96311, time = secondTime), ActivityType.WALKING).accepted)
        }
    }

    @Test
    fun stationaryMovementInsideDriftRadiusIsRejected() {
        val filter = GpsFilter()
        filter.evaluate(point(), ActivityType.STATIONARY)
        val result = filter.evaluate(point(latitude = -37.81359, time = 6_000), ActivityType.STATIONARY)
        assertFalse(result.accepted)
        assertTrue(result.rejectionReason.orEmpty().contains("Stationary GPS drift"))
    }

    @Test
    fun resetMakesNextValidPointANewAcceptedOrigin() {
        val filter = GpsFilter()
        filter.evaluate(point(), ActivityType.RUNNING)
        filter.reset()
        val result = filter.evaluate(point(latitude = -33.8688, longitude = 151.2093, time = 1), ActivityType.RUNNING)
        assertTrue(result.accepted)
        assertEquals(0.0, result.distanceDeltaMeters, 0.0)
    }

    @Test
    fun acceptedMovementIsSmoothedBetweenPreviousAndRawPoint() {
        val filter = GpsFilter()
        val first = point()
        val raw = point(latitude = -37.8135, longitude = 144.9632, time = 6_000)
        filter.evaluate(first, ActivityType.WALKING)
        val result = filter.evaluate(raw, ActivityType.WALKING)
        assertTrue(result.accepted)
        assertNotNull(result.point)
        assertTrue(result.point!!.latitude in first.latitude..raw.latitude)
        assertTrue(result.point!!.longitude in first.longitude..raw.longitude)
    }
}
