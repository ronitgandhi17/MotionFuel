package com.ronitgandhi.motionfuel.data.repository

import com.ronitgandhi.motionfuel.data.local.NutritionDao
import com.ronitgandhi.motionfuel.data.local.NutritionEntryEntity
import com.ronitgandhi.motionfuel.data.local.WorkoutDao
import com.ronitgandhi.motionfuel.data.local.WorkoutEntity
import com.ronitgandhi.motionfuel.data.network.NutritionSyncDto
import com.ronitgandhi.motionfuel.data.network.WorkoutSyncDto
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class MotionFuelRepository(
    private val workoutDao: WorkoutDao,
    private val nutritionDao: NutritionDao,
) {
    fun observeWorkouts(): Flow<List<WorkoutSummary>> = workoutDao.observeAll().map { rows -> rows.map(::toDomain) }

    fun observeNutritionEntries(start: Long, end: Long): Flow<List<NutritionEntry>> =
        nutritionDao.observeBetween(start, end).map { rows -> rows.map(::toDomain) }

    fun observeNutritionTotals(start: Long, end: Long): Flow<NutritionTotals> =
        nutritionDao.observeTotalsBetween(start, end).map {
            NutritionTotals(it.caloriesKcal, it.proteinG, it.carbohydratesG, it.fatG)
        }

    suspend fun saveWorkout(workout: WorkoutSummary) = workoutDao.upsert(
        WorkoutEntity(
            id = workout.id,
            type = workout.type.name,
            startedAtMillis = workout.startedAtMillis,
            durationSeconds = workout.durationSeconds,
            distanceMeters = workout.distanceMeters,
            averagePaceSecPerKm = workout.averagePaceSecPerKm,
            steps = workout.steps,
            elevationGainMeters = workout.elevationGainMeters,
            caloriesKcal = workout.caloriesKcal,
            dominantActivity = workout.dominantActivity.name,
            rejectedGpsPoints = workout.rejectedGpsPoints,
            routeJson = encodeRoute(workout.route),
        ),
    )

    suspend fun saveNutrition(entry: NutritionEntry) = nutritionDao.upsert(
        NutritionEntryEntity(
            id = entry.id,
            name = entry.name,
            caloriesKcal = entry.caloriesKcal,
            proteinG = entry.proteinG,
            carbohydratesG = entry.carbohydratesG,
            fatG = entry.fatG,
            mealType = entry.mealType.name,
            consumedAtMillis = entry.consumedAtMillis,
            createdOffline = entry.createdOffline,
        ),
    )

    suspend fun deleteAllLocalData() {
        workoutDao.deleteAll()
        nutritionDao.deleteAll()
    }

    // Returns locally pending workouts as sync DTOs; routes travel only with backup consent.
    suspend fun pendingWorkoutDtos(routeBackup: Boolean): List<WorkoutSyncDto> =
        workoutDao.pending().map { entity ->
            WorkoutSyncDto(
                id = entity.id,
                type = entity.type,
                startedAtMillis = entity.startedAtMillis,
                durationSeconds = entity.durationSeconds,
                distanceMeters = entity.distanceMeters,
                averagePaceSecPerKm = entity.averagePaceSecPerKm,
                steps = entity.steps,
                elevationGainMeters = entity.elevationGainMeters,
                caloriesKcal = entity.caloriesKcal,
                dominantActivity = entity.dominantActivity,
                rejectedGpsPoints = entity.rejectedGpsPoints,
                routeJson = if (routeBackup) entity.routeJson else "[]",
            )
        }

    // Returns locally pending nutrition rows as sync DTOs.
    suspend fun pendingNutritionDtos(): List<NutritionSyncDto> =
        nutritionDao.pending().map { entity ->
            NutritionSyncDto(
                id = entity.id,
                name = entity.name,
                caloriesKcal = entity.caloriesKcal,
                proteinG = entity.proteinG,
                carbohydratesG = entity.carbohydratesG,
                fatG = entity.fatG,
                mealType = entity.mealType,
                consumedAtMillis = entity.consumedAtMillis,
                createdOffline = entity.createdOffline,
            )
        }

    suspend fun markWorkoutsSynced(ids: List<String>) {
        if (ids.isNotEmpty()) workoutDao.markSynced(ids)
    }

    suspend fun markNutritionSynced(ids: List<String>) {
        if (ids.isNotEmpty()) nutritionDao.markSynced(ids)
    }

    // Mirrors workouts pulled from the backend into Room, already flagged as synced.
    suspend fun upsertRemoteWorkouts(workouts: List<WorkoutSyncDto>) {
        if (workouts.isEmpty()) return
        workoutDao.upsertAll(
            workouts.map { dto ->
                WorkoutEntity(
                    id = dto.id,
                    type = dto.type,
                    startedAtMillis = dto.startedAtMillis,
                    durationSeconds = dto.durationSeconds,
                    distanceMeters = dto.distanceMeters,
                    averagePaceSecPerKm = dto.averagePaceSecPerKm,
                    steps = dto.steps,
                    elevationGainMeters = dto.elevationGainMeters,
                    caloriesKcal = dto.caloriesKcal,
                    dominantActivity = dto.dominantActivity,
                    rejectedGpsPoints = dto.rejectedGpsPoints,
                    routeJson = dto.routeJson,
                    syncState = "SYNCED",
                )
            },
        )
    }

    // Mirrors nutrition entries pulled from the backend into Room, already flagged as synced.
    suspend fun upsertRemoteNutrition(nutrition: List<NutritionSyncDto>) {
        if (nutrition.isEmpty()) return
        nutritionDao.upsertAll(
            nutrition.map { dto ->
                NutritionEntryEntity(
                    id = dto.id,
                    name = dto.name,
                    caloriesKcal = dto.caloriesKcal,
                    proteinG = dto.proteinG,
                    carbohydratesG = dto.carbohydratesG,
                    fatG = dto.fatG,
                    mealType = dto.mealType,
                    consumedAtMillis = dto.consumedAtMillis,
                    createdOffline = dto.createdOffline,
                    syncState = "SYNCED",
                )
            },
        )
    }

    private fun toDomain(entity: WorkoutEntity) = WorkoutSummary(
        id = entity.id,
        type = enumValueOrDefault(entity.type, WorkoutType.RUN),
        startedAtMillis = entity.startedAtMillis,
        durationSeconds = entity.durationSeconds,
        distanceMeters = entity.distanceMeters,
        averagePaceSecPerKm = entity.averagePaceSecPerKm,
        steps = entity.steps,
        elevationGainMeters = entity.elevationGainMeters,
        caloriesKcal = entity.caloriesKcal,
        dominantActivity = enumValueOrDefault(entity.dominantActivity, ActivityType.UNKNOWN),
        rejectedGpsPoints = entity.rejectedGpsPoints,
        route = decodeRoute(entity.routeJson),
    )

    private fun toDomain(entity: NutritionEntryEntity) = NutritionEntry(
        id = entity.id,
        name = entity.name,
        caloriesKcal = entity.caloriesKcal,
        proteinG = entity.proteinG,
        carbohydratesG = entity.carbohydratesG,
        fatG = entity.fatG,
        mealType = enumValueOrDefault(entity.mealType, MealType.SNACK),
        consumedAtMillis = entity.consumedAtMillis,
        createdOffline = entity.createdOffline,
    )

    private fun encodeRoute(route: List<GeoPoint>): String = JSONArray().apply {
        route.forEach { point ->
            put(JSONObject().apply {
                put("lat", point.latitude)
                put("lon", point.longitude)
                point.altitudeMeters?.let { put("alt", it) }
                put("accuracy", point.accuracyMeters.toDouble())
                put("time", point.timestampMillis)
            })
        }
    }.toString()

    private fun decodeRoute(json: String): List<GeoPoint> = runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            GeoPoint(
                latitude = item.getDouble("lat"),
                longitude = item.getDouble("lon"),
                altitudeMeters = if (item.has("alt")) item.getDouble("alt") else null,
                accuracyMeters = item.optDouble("accuracy", 5.0).toFloat(),
                timestampMillis = item.getLong("time"),
            )
        }
    }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
