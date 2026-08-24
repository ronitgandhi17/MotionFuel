package com.ronitgandhi.motionfuel.domain.algorithm

import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.SensorFeatureWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFusionClassifierTest {
    private val classifier = SensorFusionClassifier()

    @Test
    fun runningWindowUsesMultipleSignals() {
        val result = classifier.classify(
            SensorFeatureWindow(2.4, 1.5, 0.38, 172.0, 3.2, 8f),
        )

        assertEquals(ActivityType.RUNNING, result.type)
        assertTrue(result.confidence > 0.55f)
        assertTrue(result.evidence.size >= 3)
    }

    @Test
    fun hysteresisRequiresTwoSupportingWindows() {
        val stabilizer = ActivityStateStabilizer(requiredConsecutiveWindows = 2)
        val running = classifier.classify(SensorFeatureWindow(2.4, 1.5, 0.38, 172.0, 3.2, 8f))

        assertEquals(ActivityType.UNKNOWN, stabilizer.update(running).type)
        assertEquals(ActivityType.RUNNING, stabilizer.update(running).type)
    }
}
