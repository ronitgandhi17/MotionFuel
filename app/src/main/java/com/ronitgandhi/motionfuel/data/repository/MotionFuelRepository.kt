package com.ronitgandhi.motionfuel.data.repository

import com.ronitgandhi.motionfuel.data.local.CustomMealDao
import com.ronitgandhi.motionfuel.data.local.CustomMealEntity
import com.ronitgandhi.motionfuel.data.local.DailySummaryDao
import com.ronitgandhi.motionfuel.data.local.DailySummaryEntity
import com.ronitgandhi.motionfuel.data.local.MaintenanceSnapshotDao
import com.ronitgandhi.motionfuel.data.local.MaintenanceSnapshotEntity
import com.ronitgandhi.motionfuel.data.local.NutritionDao
import com.ronitgandhi.motionfuel.data.local.NutritionEntryEntity
import com.ronitgandhi.motionfuel.data.local.UserProfileDao
import com.ronitgandhi.motionfuel.data.local.UserProfileEntity
import com.ronitgandhi.motionfuel.data.local.WeightDao
import com.ronitgandhi.motionfuel.data.local.WeightEntryEntity
import com.ronitgandhi.motionfuel.data.local.WorkoutDao
import com.ronitgandhi.motionfuel.data.local.WorkoutEntity
import com.ronitgandhi.motionfuel.domain.model.ActivityLevel
import com.ronitgandhi.motionfuel.domain.model.ActivityType
import com.ronitgandhi.motionfuel.domain.model.CustomMeal
import com.ronitgandhi.motionfuel.domain.model.DailySummary
import com.ronitgandhi.motionfuel.domain.model.FoodSource
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.MaintenanceSnapshot
import com.ronitgandhi.motionfuel.domain.model.MaintenanceTrigger
import com.ronitgandhi.motionfuel.domain.model.MealType
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.Sex
import com.ronitgandhi.motionfuel.domain.model.UserProfile
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import com.ronitgandhi.motionfuel.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// Single façade over the local Room database. Room is the source of truth; the UI observes it and
// FirestoreGateway drains the PENDING rows to the signed-in user's private Firestore scope.
class MotionFuelRepository(
    private val workoutDao: WorkoutDao,
    private val nutritionDao: NutritionDao,
    private val profileDao: UserProfileDao,
    private val weightDao: WeightDao,
    private val customMealDao: CustomMealDao,
    private val dailySummaryDao: DailySummaryDao,
    private val maintenanceSnapshotDao: MaintenanceSnapshotDao,
) {
    // ---- Observation ----
    fun observeWorkouts(): Flow<List<WorkoutSummary>> = workoutDao.observeAll().map { it.map(::toDomain) }

    fun observeNutritionEntries(start: Long, end: Long): Flow<List<NutritionEntry>> =
        nutritionDao.observeBetween(start, end).map { it.map(::toDomain) }

    fun observeNutritionTotals(start: Long, end: Long): Flow<NutritionTotals> =
        nutritionDao.observeTotalsBetween(start, end).map {
            NutritionTotals(it.caloriesKcal, it.proteinG, it.carbohydratesG, it.fatG)
        }

    fun observeProfile(): Flow<UserProfile?> = profileDao.observe().map { it?.let(::toDomain) }
    suspend fun currentProfile(): UserProfile? = profileDao.current()?.let(::toDomain)

    fun observeWeights(): Flow<List<WeightEntry>> = weightDao.observeAll().map { it.map(::toDomain) }
    fun observeCustomMeals(): Flow<List<CustomMeal>> = customMealDao.observeAll().map { it.map(::toDomain) }
    fun observeDailySummaries(limit: Int): Flow<List<DailySummary>> =
        dailySummaryDao.observeRecent(limit).map { it.map(::toDomain) }
    fun observeMaintenanceSnapshots(): Flow<List<MaintenanceSnapshot>> =
        maintenanceSnapshotDao.observeAll().map { it.map(::toDomain) }

    // ---- Writes (local-first; each new/edited row is PENDING until the cloud confirms) ----
    suspend fun saveWorkout(workout: WorkoutSummary) = workoutDao.upsert(toEntity(workout))

    suspend fun saveNutrition(entry: NutritionEntry) = nutritionDao.upsert(toEntity(entry))
    suspend fun deleteNutrition(id: String) = nutritionDao.delete(id)

    suspend fun saveProfile(profile: UserProfile) = profileDao.upsert(toEntity(profile))

    suspend fun saveWeight(entry: WeightEntry) = weightDao.upsert(toEntity(entry))
    suspend fun saveCustomMeal(meal: CustomMeal) = customMealDao.upsert(toEntity(meal))
    suspend fun deleteCustomMeal(id: String) = customMealDao.delete(id)
    suspend fun upsertDailySummary(summary: DailySummary) = dailySummaryDao.upsert(toEntity(summary))
    suspend fun saveMaintenanceSnapshot(snapshot: MaintenanceSnapshot) =
        maintenanceSnapshotDao.upsert(toEntity(snapshot))

    suspend fun deleteAllLocalData() {
        workoutDao.deleteAll()
        nutritionDao.deleteAll()
        profileDao.deleteAll()
        weightDao.deleteAll()
        customMealDao.deleteAll()
        dailySummaryDao.deleteAll()
        maintenanceSnapshotDao.deleteAll()
    }

    // ---- Sync accessors (entities are handed straight to the Firestore gateway) ----
    suspend fun pendingWorkouts() = workoutDao.pending()
    suspend fun pendingNutrition() = nutritionDao.pending()
    suspend fun pendingProfiles() = profileDao.pending()
    suspend fun pendingWeights() = weightDao.pending()
    suspend fun pendingCustomMeals() = customMealDao.pending()
    suspend fun pendingDailySummaries() = dailySummaryDao.pending()
    suspend fun pendingMaintenance() = maintenanceSnapshotDao.pending()

    suspend fun markWorkoutsSynced(ids: List<String>) { if (ids.isNotEmpty()) workoutDao.markSynced(ids) }
    suspend fun markNutritionSynced(ids: List<String>) { if (ids.isNotEmpty()) nutritionDao.markSynced(ids) }
    suspend fun markProfilesSynced(ids: List<String>) { if (ids.isNotEmpty()) profileDao.markSynced(ids) }
    suspend fun markWeightsSynced(ids: List<String>) { if (ids.isNotEmpty()) weightDao.markSynced(ids) }
    suspend fun markCustomMealsSynced(ids: List<String>) { if (ids.isNotEmpty()) customMealDao.markSynced(ids) }
    suspend fun markDailySummariesSynced(ids: List<String>) { if (ids.isNotEmpty()) dailySummaryDao.markSynced(ids) }
    suspend fun markMaintenanceSynced(ids: List<String>) { if (ids.isNotEmpty()) maintenanceSnapshotDao.markSynced(ids) }

    suspend fun upsertRemoteWorkouts(rows: List<WorkoutEntity>) { if (rows.isNotEmpty()) workoutDao.upsertAll(rows) }
    suspend fun upsertRemoteNutrition(rows: List<NutritionEntryEntity>) { if (rows.isNotEmpty()) nutritionDao.upsertAll(rows) }
    suspend fun upsertRemoteWeights(rows: List<WeightEntryEntity>) { if (rows.isNotEmpty()) weightDao.upsertAll(rows) }
    suspend fun upsertRemoteCustomMeals(rows: List<CustomMealEntity>) { if (rows.isNotEmpty()) customMealDao.upsertAll(rows) }
    suspend fun upsertRemoteDailySummaries(rows: List<DailySummaryEntity>) { if (rows.isNotEmpty()) dailySummaryDao.upsertAll(rows) }
    suspend fun upsertRemoteMaintenance(rows: List<MaintenanceSnapshotEntity>) { if (rows.isNotEmpty()) maintenanceSnapshotDao.upsertAll(rows) }
    suspend fun upsertRemoteProfile(row: UserProfileEntity) = profileDao.upsert(row)

    // ---- Route JSON (used by workouts; only uploaded when route-backup is on) ----
    fun encodeRoute(route: List<GeoPoint>): String = JSONArray().apply {
        route.forEach { point ->
            put(
                JSONObject().apply {
                    put("lat", point.latitude)
                    put("lon", point.longitude)
                    point.altitudeMeters?.let { put("alt", it) }
                    put("accuracy", point.accuracyMeters.toDouble())
                    put("time", point.timestampMillis)
                },
            )
        }
    }.toString()

    fun decodeRoute(json: String): List<GeoPoint> = runCatching {
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

    // ---- Mapping ----
    private fun toEntity(w: WorkoutSummary) = WorkoutEntity(
        id = w.id, type = w.type.name, startedAtMillis = w.startedAtMillis,
        durationSeconds = w.durationSeconds, distanceMeters = w.distanceMeters,
        averagePaceSecPerKm = w.averagePaceSecPerKm, steps = w.steps,
        elevationGainMeters = w.elevationGainMeters, caloriesKcal = w.caloriesKcal,
        dominantActivity = w.dominantActivity.name, rejectedGpsPoints = w.rejectedGpsPoints,
        routeJson = encodeRoute(w.route),
    )

    private fun toDomain(e: WorkoutEntity) = WorkoutSummary(
        id = e.id, type = enumOr(e.type, WorkoutType.RUN), startedAtMillis = e.startedAtMillis,
        durationSeconds = e.durationSeconds, distanceMeters = e.distanceMeters,
        averagePaceSecPerKm = e.averagePaceSecPerKm, steps = e.steps,
        elevationGainMeters = e.elevationGainMeters, caloriesKcal = e.caloriesKcal,
        dominantActivity = enumOr(e.dominantActivity, ActivityType.UNKNOWN),
        rejectedGpsPoints = e.rejectedGpsPoints, route = decodeRoute(e.routeJson),
    )

    private fun toEntity(n: NutritionEntry) = NutritionEntryEntity(
        id = n.id, name = n.name, caloriesKcal = n.caloriesKcal, proteinG = n.proteinG,
        carbohydratesG = n.carbohydratesG, fatG = n.fatG, mealType = n.mealType.name,
        consumedAtMillis = n.consumedAtMillis, createdOffline = n.createdOffline,
        servingLabel = n.servingLabel, quantity = n.quantity, source = n.source.name, photoUri = n.photoUri,
    )

    private fun toDomain(e: NutritionEntryEntity) = NutritionEntry(
        id = e.id, name = e.name, caloriesKcal = e.caloriesKcal, proteinG = e.proteinG,
        carbohydratesG = e.carbohydratesG, fatG = e.fatG, mealType = enumOr(e.mealType, MealType.SNACK),
        consumedAtMillis = e.consumedAtMillis, createdOffline = e.createdOffline,
        servingLabel = e.servingLabel, quantity = e.quantity, source = enumOr(e.source, FoodSource.SEARCH),
        photoUri = e.photoUri,
    )

    private fun toEntity(p: UserProfile) = UserProfileEntity(
        uid = p.uid, displayName = p.displayName, email = p.email, age = p.age, sex = p.sex.name,
        heightCm = p.heightCm, weightKg = p.weightKg, activityLevel = p.activityLevel.name,
        maintenanceCalories = p.maintenanceCalories, dailyCalorieGoal = p.dailyCalorieGoal,
        proteinTargetG = p.proteinTargetG, createdAtMillis = p.createdAtMillis, profileComplete = p.profileComplete,
    )

    private fun toDomain(e: UserProfileEntity) = UserProfile(
        uid = e.uid, displayName = e.displayName, email = e.email, age = e.age,
        sex = enumOr(e.sex, Sex.MALE), heightCm = e.heightCm, weightKg = e.weightKg,
        activityLevel = enumOr(e.activityLevel, ActivityLevel.MODERATE),
        maintenanceCalories = e.maintenanceCalories, dailyCalorieGoal = e.dailyCalorieGoal,
        proteinTargetG = e.proteinTargetG, createdAtMillis = e.createdAtMillis, profileComplete = e.profileComplete,
    )

    private fun toEntity(w: WeightEntry) = WeightEntryEntity(w.id, w.weightKg, w.recordedAtMillis)
    private fun toDomain(e: WeightEntryEntity) = WeightEntry(e.id, e.weightKg, e.recordedAtMillis)

    private fun toEntity(m: CustomMeal) = CustomMealEntity(
        id = m.id, name = m.name, caloriesKcal = m.caloriesKcal, proteinG = m.proteinG,
        carbohydratesG = m.carbohydratesG, fatG = m.fatG, photoUri = m.photoUri, createdAtMillis = m.createdAtMillis,
    )

    private fun toDomain(e: CustomMealEntity) = CustomMeal(
        id = e.id, name = e.name, caloriesKcal = e.caloriesKcal, proteinG = e.proteinG,
        carbohydratesG = e.carbohydratesG, fatG = e.fatG, photoUri = e.photoUri, createdAtMillis = e.createdAtMillis,
    )

    private fun toEntity(s: DailySummary) = DailySummaryEntity(
        dateKey = s.dateKey, calorieTarget = s.calorieTarget, caloriesConsumed = s.caloriesConsumed,
        proteinG = s.proteinG, carbohydratesG = s.carbohydratesG, fatG = s.fatG,
        breakfastKcal = s.breakfastKcal, lunchKcal = s.lunchKcal, dinnerKcal = s.dinnerKcal,
        snackKcal = s.snackKcal, workoutBurnKcal = s.workoutBurnKcal, steps = s.steps, latestWeightKg = s.latestWeightKg,
    )

    private fun toDomain(e: DailySummaryEntity) = DailySummary(
        dateKey = e.dateKey, calorieTarget = e.calorieTarget, caloriesConsumed = e.caloriesConsumed,
        proteinG = e.proteinG, carbohydratesG = e.carbohydratesG, fatG = e.fatG,
        breakfastKcal = e.breakfastKcal, lunchKcal = e.lunchKcal, dinnerKcal = e.dinnerKcal,
        snackKcal = e.snackKcal, workoutBurnKcal = e.workoutBurnKcal, steps = e.steps, latestWeightKg = e.latestWeightKg,
    )

    private fun toEntity(s: MaintenanceSnapshot) = MaintenanceSnapshotEntity(
        id = s.id, calculatedAtMillis = s.calculatedAtMillis, bmr = s.bmr, tdee = s.tdee,
        weightKg = s.weightKg, activityLevel = s.activityLevel.name, trigger = s.trigger.name,
    )

    private fun toDomain(e: MaintenanceSnapshotEntity) = MaintenanceSnapshot(
        id = e.id, calculatedAtMillis = e.calculatedAtMillis, bmr = e.bmr, tdee = e.tdee,
        weightKg = e.weightKg, activityLevel = enumOr(e.activityLevel, ActivityLevel.MODERATE),
        trigger = enumOr(e.trigger, MaintenanceTrigger.INITIAL),
    )

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
