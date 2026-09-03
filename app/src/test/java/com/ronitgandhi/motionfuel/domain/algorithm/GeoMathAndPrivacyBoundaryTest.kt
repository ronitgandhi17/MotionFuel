package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathAndPrivacyBoundaryTest {
    private fun point(latitude: Double, longitude: Double, time: Long = 0) =
        GeoPoint(latitude, longitude, timestampMillis = time)

    @Test
    fun identicalCoordinatesHaveZeroDistance() {
        val melbourne = point(-37.8136, 144.9631)
        assertEquals(0.0, GeoMath.distanceMeters(melbourne, melbourne), 0.0)
    }

    @Test
    fun distanceIsSymmetricAcrossRepresentativeCoordinates() {
        val pairs = listOf(
            point(-37.8136, 144.9631) to point(-33.8688, 151.2093),
            point(0.0, 0.0) to point(45.0, 90.0),
            point(89.0, -179.0) to point(-89.0, 179.0),
        )
        pairs.forEach { (a, b) -> assertEquals(GeoMath.distanceMeters(a, b), GeoMath.distanceMeters(b, a), 0.000001) }
    }

    @Test
    fun melbourneToSydneyDistanceIsWithinExpectedRange() {
        val distance = GeoMath.distanceMeters(point(-37.8136, 144.9631), point(-33.8688, 151.2093))
        assertTrue(distance in 710_000.0..720_000.0)
    }

    @Test
    fun antipodalDistanceDoesNotExceedHalfEarthCircumference() {
        val distance = GeoMath.distanceMeters(point(0.0, 0.0), point(0.0, 180.0))
        assertTrue(distance in 20_000_000.0..20_020_000.0)
    }

    @Test
    fun noPrivacyZonesReturnsOriginalRouteInstance() {
        val route = listOf(point(-37.8, 144.9), point(-37.7, 145.0))
        assertSame(route, PrivacyZoneMasker.mask(route, emptyList()))
    }

    @Test
    fun multiplePrivacyZonesRemoveOnlyCoveredPoints() {
        val first = point(-37.8136, 144.9631)
        val middle = point(-37.8100, 144.9700)
        val last = point(-37.8000, 144.9800)
        val masked = PrivacyZoneMasker.mask(
            listOf(first, middle, last),
            listOf(PrivacyZone(first, 50.0), PrivacyZone(last, 50.0)),
        )
        assertEquals(listOf(middle), masked)
    }

    @Test
    fun emptyRouteRemainsEmptyWithPrivacyZones() {
        assertTrue(PrivacyZoneMasker.mask(emptyList(), listOf(PrivacyZone(point(0.0, 0.0), 100.0))).isEmpty())
    }
}
