package com.ronitgandhi.motionfuel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.share.GpxCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EverydayToolsDeviceTest {
    @Test fun androidParserImportsExportedTrack() {
        val points = listOf(GeoPoint(1.0, 2.0, timestampMillis = 1000), GeoPoint(1.1, 2.1, timestampMillis = 2000))
        assertEquals(points, GpxCodec.read(GpxCodec.write("Test", points).byteInputStream()))
    }
    @Test fun androidParserRejectsDtdBeforeResolvingIt() {
        try {
            GpxCodec.read("<!DOCTYPE gpx SYSTEM 'https://example.com/external'><gpx/>".byteInputStream())
            fail("External document must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
