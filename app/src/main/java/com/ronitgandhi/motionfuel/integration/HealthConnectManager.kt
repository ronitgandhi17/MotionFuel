package com.ronitgandhi.motionfuel.integration

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.ronitgandhi.motionfuel.domain.model.ConnectedHealthSnapshot
import java.time.Instant
import java.time.ZoneId

class HealthConnectManager(private val context: Context) {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun readToday(): ConnectedHealthSnapshot {
        if (!isAvailable()) return ConnectedHealthSnapshot(status = "Health Connect is not available on this device")
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(permissions)) return ConnectedHealthSnapshot(available = true, status = "Permission required")
        val zone = ZoneId.systemDefault()
        val start = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val end = Instant.now()
        val range = TimeRangeFilter.between(start, end)
        val aggregate = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = range,
            ),
        )
        val weights = client.readRecords(ReadRecordsRequest(WeightRecord::class, timeRangeFilter = TimeRangeFilter.before(end))).records
        val sleeps = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start.minusSeconds(18 * 3600), end))).records
        val heart = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = range)).records
        val sleepSeconds = sleeps.sumOf { java.time.Duration.between(it.startTime, it.endTime).seconds.coerceAtLeast(0) }
        val heartSamples = heart.flatMap { it.samples }.map { it.beatsPerMinute }
        return ConnectedHealthSnapshot(
            available = true,
            permissionsGranted = true,
            stepsToday = aggregate[StepsRecord.COUNT_TOTAL] ?: 0L,
            activeCaloriesKcal = aggregate[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
            latestWeightKg = weights.maxByOrNull { it.time }?.weight?.inKilograms,
            sleepHours = sleepSeconds / 3_600.0,
            restingHeartRateBpm = heartSamples.minOrNull()?.toInt(),
            status = "Synced ${java.time.LocalTime.now(zone).withSecond(0).withNano(0)}",
        )
    }
}
