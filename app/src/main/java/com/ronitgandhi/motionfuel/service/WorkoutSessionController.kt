package com.ronitgandhi.motionfuel.service

import com.ronitgandhi.motionfuel.domain.model.WorkoutTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WorkoutSessionController {
    private val mutableTelemetry = MutableStateFlow(WorkoutTelemetry())
    val telemetry: StateFlow<WorkoutTelemetry> = mutableTelemetry.asStateFlow()

    fun publish(value: WorkoutTelemetry) {
        mutableTelemetry.value = value
    }

    fun update(block: (WorkoutTelemetry) -> WorkoutTelemetry) {
        mutableTelemetry.value = block(mutableTelemetry.value)
    }

    fun reset() {
        mutableTelemetry.value = WorkoutTelemetry()
    }
}
