package com.ronitgandhi.motionfuel.features

import com.ronitgandhi.motionfuel.domain.algorithm.AutoPauseDetector
import com.ronitgandhi.motionfuel.domain.algorithm.FoodPlanning
import com.ronitgandhi.motionfuel.domain.algorithm.TrainingAnalysis
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.share.GpxCodec
import org.junit.Assert.*
import org.junit.Test

class EverydayToolsTest {
    private fun point(lat: Double, lon: Double, altitude: Double? = null) = GeoPoint(lat, lon, altitude, timestampMillis = 1000L)
    @Test fun gpxRoundTripPreservesCoordinatesAndElevation() {
        val points = listOf(point(-37.8, 144.9, 10.0), point(-37.81, 144.91, 20.0))
        assertEquals(points, GpxCodec.read(GpxCodec.write("A & B <route>", points).byteInputStream()))
    }
    @Test fun gpxAcceptsRoutePointFiles() {
        val xml = "<gpx><rte><rtept lat='1' lon='2'/><rtept lat='3' lon='4'/></rte></gpx>"
        assertEquals(2, GpxCodec.read(xml.byteInputStream()).size)
    }
    @Test fun gpxRejectsExternalEntities() {
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.read("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><gpx>&x;</gpx>".byteInputStream()) }
    }
    @Test fun gpxRejectsOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.read("<gpx><trkpt lat='91' lon='0'/><trkpt lat='1' lon='0'/></gpx>".byteInputStream()) }
    }
    @Test fun gpxRejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.read("<gpx><trkpt lat='NaN' lon='0'/><trkpt lat='1' lon='0'/></gpx>".byteInputStream()) }
    }
    @Test fun gpxRejectsOversizedFileBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.read(ByteArray(GpxCodec.MAX_BYTES + 1).inputStream()) }
    }
    @Test fun autoPauseRequiresTenSecondsOfReliableSamples() {
        val detector = AutoPauseDetector()
        assertFalse(detector.shouldPause(0.1, 5f, 0))
        assertFalse(detector.shouldPause(0.1, 5f, 9999))
        assertTrue(detector.shouldPause(0.1, 5f, 10000))
        assertFalse(detector.shouldPause(1.0, 5f, 11000))
    }
    @Test fun invalidSpeedResetsStopDuration() {
        val detector = AutoPauseDetector()
        detector.shouldPause(0.1, 5f, 0)
        assertFalse(detector.shouldPause(Double.NaN, 5f, 15000))
        assertFalse(detector.shouldPause(0.1, 5f, 16000))
    }
    @Test fun poorAccuracyCannotPauseAWorkout() {
        val detector = AutoPauseDetector()
        detector.shouldPause(0.0, 5f, 0)
        assertFalse(detector.shouldPause(0.0, 70f, 20000))
    }
    @Test fun elevationDeadBandSuppressesJitter() {
        val result = TrainingAnalysis.elevation(listOf(10.0, 11.0, 10.5, 15.0, 9.0).map { point(0.0, 0.0, it) })
        assertEquals(5.0, result.ascent, 0.001)
        assertEquals(6.0, result.descent, 0.001)
    }
    @Test fun missingElevationStaysUnavailable() { assertTrue(TrainingAnalysis.elevation(listOf(point(0.0, 0.0))).samples.isEmpty()) }
    @Test fun batteryModesAreBounded() {
        assertEquals(1000L, TrainingAnalysis.locationInterval(10, true, false))
        assertEquals(5000L, TrainingAnalysis.locationInterval(10, false, true))
        assertEquals(3000L, TrainingAnalysis.locationInterval(90, true, true))
    }
    @Test fun groceryListAggregatesNamesAndSubtractsPantry() {
        val rows = FoodPlanning.groceryList(listOf(FoodPlanning.Ingredient("Rice", 2.0), FoodPlanning.Ingredient(" rice ", 1.0)), listOf(FoodPlanning.Ingredient("RICE", 1.5)))
        assertEquals(listOf(FoodPlanning.Ingredient("rice", 1.5)), rows)
    }
    @Test fun groceryListOmitsCoveredAndInvalidQuantities() {
        assertTrue(FoodPlanning.groceryList(listOf(FoodPlanning.Ingredient("Rice", 1.0), FoodPlanning.Ingredient("Salt", Double.NaN)), listOf(FoodPlanning.Ingredient("Rice", 2.0))).isEmpty())
    }
    @Test fun allergyFiltersRequireConfirmedMetadata() {
        assertFalse(FoodPlanning.allowed(emptySet(), emptySet(), "Any", setOf("Milk"), false))
        assertFalse(FoodPlanning.allowed(setOf("Vegetarian"), setOf("Milk"), "Vegetarian", setOf("Milk"), true))
        assertTrue(FoodPlanning.allowed(setOf("Vegan"), emptySet(), "Vegan", setOf("Milk"), true))
        assertTrue(FoodPlanning.allowed(setOf("Vegan"), emptySet(), "Vegetarian", emptySet(), true))
    }
}
