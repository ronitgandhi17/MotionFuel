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
import com.ronitgandhi.motionfuel.data.local.RecipeDao
import com.ronitgandhi.motionfuel.data.local.RecipeEntity
import com.ronitgandhi.motionfuel.data.local.PlannedWorkoutDao
import com.ronitgandhi.motionfuel.data.local.PlannedWorkoutEntity
import com.ronitgandhi.motionfuel.data.local.PlannedRouteDao
import com.ronitgandhi.motionfuel.data.local.PlannedRouteEntity
import com.ronitgandhi.motionfuel.data.local.ChallengeDao
import com.ronitgandhi.motionfuel.data.local.ChallengeEntity
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
import com.ronitgandhi.motionfuel.domain.model.Recipe
import com.ronitgandhi.motionfuel.domain.model.RecipeIngredient
import com.ronitgandhi.motionfuel.domain.model.PlannedWorkout
import com.ronitgandhi.motionfuel.domain.model.PlannedRoute
import com.ronitgandhi.motionfuel.domain.model.Challenge
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
    private val recipeDao: RecipeDao,
    private val plannedWorkoutDao: PlannedWorkoutDao,
    private val plannedRouteDao: PlannedRouteDao,
    private val challengeDao: ChallengeDao,
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

    fun observeRecipes(): Flow<List<Recipe>> = recipeDao.observeAll().map { rows -> rows.map(::toDomain) }
    fun observePlannedWorkouts(): Flow<List<PlannedWorkout>> = plannedWorkoutDao.observeAll().map { rows -> rows.map(::toDomain) }
    fun observePlannedRoutes(): Flow<List<PlannedRoute>> = plannedRouteDao.observeAll().map { rows -> rows.map(::toDomain) }
    fun observeChallenges(): Flow<List<Challenge>> = challengeDao.observeAll().map { rows -> rows.map(::toDomain) }

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

    suspend fun saveRecipe(recipe: Recipe) = recipeDao.upsert(RecipeEntity(recipe.id, recipe.name, encodeIngredients(recipe.ingredients), recipe.servings, recipe.createdAtMillis))
    suspend fun deleteRecipe(id: String) = recipeDao.deleteById(id)
    suspend fun savePlannedWorkout(item: PlannedWorkout) = plannedWorkoutDao.upsert(PlannedWorkoutEntity(item.id, item.type.name, item.scheduledAtMillis, item.targetDistanceMeters, item.targetDurationMinutes, item.completed))
    suspend fun deletePlannedWorkout(id: String) = plannedWorkoutDao.deleteById(id)
    suspend fun savePlannedRoute(item: PlannedRoute) = plannedRouteDao.upsert(PlannedRouteEntity(item.id, item.name, item.targetDistanceMeters, encodeRoute(item.points), item.createdAtMillis, item.availableOffline))
    suspend fun deletePlannedRoute(id: String) = plannedRouteDao.deleteById(id)
    suspend fun saveChallenge(item: Challenge) = challengeDao.upsert(ChallengeEntity(item.id, item.title, item.metric, item.target, item.progress, item.endsAtMillis, item.ownerUid))
    suspend fun deleteChallenge(id: String) = challengeDao.deleteById(id)

    suspend fun deleteAllLocalData() {
        workoutDao.deleteAll()
        nutritionDao.deleteAll()
        weightDao.deleteAll()
        savedFoodDao.deleteAll()
        hydrationDao.deleteAll()
        mealPlanDao.deleteAll()
        recipeDao.deleteAll()
        plannedWorkoutDao.deleteAll()
        plannedRouteDao.deleteAll()
        challengeDao.deleteAll()
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

    private fun toDomain(entity: RecipeEntity) = Recipe(entity.id, entity.name, decodeIngredients(entity.ingredientsJson), entity.servings, entity.createdAtMillis)
    private fun toDomain(entity: PlannedWorkoutEntity) = PlannedWorkout(entity.id, enumValueOrDefault(entity.type, WorkoutType.WALK), entity.scheduledAtMillis, entity.targetDistanceMeters, entity.targetDurationMinutes, entity.completed)
    private fun toDomain(entity: PlannedRouteEntity) = PlannedRoute(entity.id, entity.name, entity.targetDistanceMeters, decodeRoute(entity.routeJson), entity.createdAtMillis, entity.availableOffline)
    private fun toDomain(entity: ChallengeEntity) = Challenge(entity.id, entity.title, entity.metric, entity.target, entity.progress, entity.endsAtMillis, entity.ownerUid)

    private fun encodeIngredients(items: List<RecipeIngredient>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().apply {
            put("name", item.name); put("servings", item.servings); put("calories", item.caloriesKcal)
            put("protein", item.proteinG); put("carbs", item.carbohydratesG); put("fat", item.fatG)
        }) }
    }.toString()

    private fun decodeIngredients(json: String): List<RecipeIngredient> = runCatching {
        val array = JSONArray(json)
        List(array.length()) { index -> array.getJSONObject(index).let {
            RecipeIngredient(it.getString("name"), it.optDouble("servings", 1.0), it.optDouble("calories"), it.optDouble("protein"), it.optDouble("carbs"), it.optDouble("fat"))
        } }
    }.getOrDefault(emptyList())

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
