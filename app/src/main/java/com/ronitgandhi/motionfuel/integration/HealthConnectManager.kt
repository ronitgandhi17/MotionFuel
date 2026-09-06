package com.ronitgandhi.motionfuel.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun unavailableMessage(): String = when (sdkStatus()) {
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect must be installed or updated"
        else -> "Health Connect is not supported by this device or emulator"
    }

    fun setupIntent(): Intent = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun readToday(): ConnectedHealthSnapshot {
        if (!isAvailable()) return ConnectedHealthSnapshot(status = unavailableMessage())
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
