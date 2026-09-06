package com.ronitgandhi.motionfuel.data.repository

import com.ronitgandhi.motionfuel.data.local.NutritionDao
import com.ronitgandhi.motionfuel.data.local.NutritionEntryEntity
import com.ronitgandhi.motionfuel.data.local.SavedFoodDao
import com.ronitgandhi.motionfuel.data.local.SavedFoodEntity
import com.ronitgandhi.motionfuel.data.local.WorkoutDao
import com.ronitgandhi.motionfuel.data.local.WorkoutEntity
import com.ronitgandhi.motionfuel.data.local.WeightDao
import com.ronitgandhi.motionfuel.data.local.WeightEntryEntity
import com.ronitgandhi.motionfuel.data.local.HydrationDao
import com.ronitgandhi.motionfuel.data.local.HydrationEntryEntity
import com.ronitgandhi.motionfuel.data.local.MealPlanDao
import com.ronitgandhi.motionfuel.data.local.MealPlanEntryEntity
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.SavedFood
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.domain.model.HydrationEntry
import com.ronitgandhi.motionfuel.domain.model.MealPlanEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class MotionFuelRepository(
    private val workoutDao: WorkoutDao,
    private val nutritionDao: NutritionDao,
    private val weightDao: WeightDao,
    private val savedFoodDao: SavedFoodDao,
    private val hydrationDao: HydrationDao,
    private val mealPlanDao: MealPlanDao,
) {
    fun observeWorkouts(): Flow<List<WorkoutSummary>> = workoutDao.observeAll().map { rows -> rows.map(::toDomain) }

    fun observeNutritionEntries(start: Long, end: Long): Flow<List<NutritionEntry>> =
        nutritionDao.observeBetween(start, end).map { rows -> rows.map(::toDomain) }

    fun observeAllNutritionEntries(): Flow<List<NutritionEntry>> =
        nutritionDao.observeAll().map { rows -> rows.map(::toDomain) }

    fun observeNutritionTotals(start: Long, end: Long): Flow<NutritionTotals> =
        nutritionDao.observeTotalsBetween(start, end).map {
            NutritionTotals(it.caloriesKcal, it.proteinG, it.carbohydratesG, it.fatG)
        }

    fun observeWeightEntries(start: Long): Flow<List<WeightEntry>> =
        weightDao.observeSince(start).map { rows -> rows.map { WeightEntry(it.id, it.weightKg, it.recordedAtMillis) } }

    fun observeAllWeightEntries(): Flow<List<WeightEntry>> =
        weightDao.observeAll().map { rows -> rows.map { WeightEntry(it.id, it.weightKg, it.recordedAtMillis) } }

    fun observeSavedFoods(): Flow<List<SavedFood>> = savedFoodDao.observeAll().map { rows -> rows.map(::toDomain) }

    fun observeHydration(start: Long, end: Long): Flow<List<HydrationEntry>> =
        hydrationDao.observeBetween(start, end).map { rows -> rows.map { HydrationEntry(it.id, it.amountMl, it.consumedAtMillis) } }

    fun observeAllHydration(): Flow<List<HydrationEntry>> =
        hydrationDao.observeAll().map { rows -> rows.map { HydrationEntry(it.id, it.amountMl, it.consumedAtMillis) } }

    fun observeMealPlan(start: Long, end: Long): Flow<List<MealPlanEntry>> =
        mealPlanDao.observeBetween(start, end).map { rows -> rows.map(::toDomain) }

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

    suspend fun deleteNutrition(id: String) = nutritionDao.deleteById(id)

    suspend fun saveWeight(entry: WeightEntry) = weightDao.upsert(
        WeightEntryEntity(entry.id, entry.weightKg, entry.recordedAtMillis),
    )

    suspend fun saveFood(food: SavedFood) = savedFoodDao.upsert(
        SavedFoodEntity(food.id, food.name, food.caloriesKcal, food.proteinG, food.carbohydratesG, food.fatG, food.photoUri, food.createdAtMillis),
    )

    suspend fun deleteFood(id: String) = savedFoodDao.deleteById(id)

    suspend fun saveHydration(entry: HydrationEntry) = hydrationDao.upsert(HydrationEntryEntity(entry.id, entry.amountMl, entry.consumedAtMillis))

    suspend fun saveMealPlan(entry: MealPlanEntry) = mealPlanDao.upsert(
        MealPlanEntryEntity(entry.id, entry.scheduledDayStartMillis, entry.mealType.name, entry.savedFoodId, entry.foodName, entry.caloriesKcal, entry.proteinG, entry.carbohydratesG, entry.fatG),
    )

    suspend fun deleteMealPlan(id: String) = mealPlanDao.deleteById(id)

    suspend fun deleteAllLocalData() {
        workoutDao.deleteAll()
        nutritionDao.deleteAll()
        weightDao.deleteAll()
        savedFoodDao.deleteAll()
        hydrationDao.deleteAll()
        mealPlanDao.deleteAll()
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

    private fun toDomain(entity: SavedFoodEntity) = SavedFood(
        entity.id,
        entity.name,
        entity.caloriesKcal,
        entity.proteinG,
        entity.carbohydratesG,
        entity.fatG,
        entity.photoUri,
        entity.createdAtMillis,
    )

    private fun toDomain(entity: MealPlanEntryEntity) = MealPlanEntry(
        entity.id,
        entity.scheduledDayStartMillis,
        enumValueOrDefault(entity.mealType, MealType.SNACK),
        entity.savedFoodId,
        entity.foodName,
        entity.caloriesKcal,
        entity.proteinG,
        entity.carbohydratesG,
        entity.fatG,
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
