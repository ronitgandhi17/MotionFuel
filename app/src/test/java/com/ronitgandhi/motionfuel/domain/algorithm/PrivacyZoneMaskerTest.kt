package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyZoneMaskerTest {
    @Test
    fun removesPointsInsideSensitiveRadius() {
        val centre = GeoPoint(-37.8136, 144.9631, timestampMillis = 0)
        val nearby = GeoPoint(-37.8135, 144.9632, timestampMillis = 1)
        val far = GeoPoint(-37.8036, 144.9731, timestampMillis = 2)

        val masked = PrivacyZoneMasker.mask(listOf(centre, nearby, far), listOf(PrivacyZone(centre, 100.0)))

        assertEquals(listOf(far), masked)
    }
}
