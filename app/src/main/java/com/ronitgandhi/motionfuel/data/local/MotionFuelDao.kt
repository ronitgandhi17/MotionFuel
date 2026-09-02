package com.ronitgandhi.motionfuel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE syncState = 'PENDING' ORDER BY startedAtMillis ASC")
    suspend fun pending(): List<WorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WorkoutEntity>)

    @Query("UPDATE workouts SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition_entries WHERE consumedAtMillis >= :start AND consumedAtMillis < :end ORDER BY consumedAtMillis DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<NutritionEntryEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(caloriesKcal), 0) AS caloriesKcal,
               COALESCE(SUM(proteinG), 0) AS proteinG,
               COALESCE(SUM(carbohydratesG), 0) AS carbohydratesG,
               COALESCE(SUM(fatG), 0) AS fatG
        FROM nutrition_entries
        WHERE consumedAtMillis >= :start AND consumedAtMillis < :end
        """,
    )
    fun observeTotalsBetween(start: Long, end: Long): Flow<NutritionTotalsProjection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: NutritionEntryEntity)

    @Query("DELETE FROM nutrition_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM nutrition_entries WHERE syncState = 'PENDING' ORDER BY consumedAtMillis ASC")
    suspend fun pending(): List<NutritionEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NutritionEntryEntity>)

    @Query("UPDATE nutrition_entries SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM nutrition_entries")
    suspend fun deleteAll()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun current(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE syncState = 'PENDING'")
    suspend fun pending(): List<UserProfileEntity>

    @Query("UPDATE user_profile SET syncState = 'SYNCED' WHERE uid IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entries ORDER BY recordedAtMillis DESC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries WHERE syncState = 'PENDING' ORDER BY recordedAtMillis ASC")
    suspend fun pending(): List<WeightEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WeightEntryEntity>)

    @Query("UPDATE weight_entries SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()
}

@Dao
interface CustomMealDao {
    @Query("SELECT * FROM custom_meals ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<CustomMealEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meal: CustomMealEntity)

    @Query("DELETE FROM custom_meals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM custom_meals WHERE syncState = 'PENDING' ORDER BY createdAtMillis ASC")
    suspend fun pending(): List<CustomMealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CustomMealEntity>)

    @Query("UPDATE custom_meals SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM custom_meals")
    suspend fun deleteAll()
}

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summaries ORDER BY dateKey DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailySummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summaries WHERE syncState = 'PENDING' ORDER BY dateKey ASC")
    suspend fun pending(): List<DailySummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<DailySummaryEntity>)

    @Query("UPDATE daily_summaries SET syncState = 'SYNCED' WHERE dateKey IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM daily_summaries")
    suspend fun deleteAll()
}

@Dao
interface MaintenanceSnapshotDao {
    @Query("SELECT * FROM maintenance_snapshots ORDER BY calculatedAtMillis DESC")
    fun observeAll(): Flow<List<MaintenanceSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: MaintenanceSnapshotEntity)

    @Query("SELECT * FROM maintenance_snapshots WHERE syncState = 'PENDING' ORDER BY calculatedAtMillis ASC")
    suspend fun pending(): List<MaintenanceSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<MaintenanceSnapshotEntity>)

    @Query("UPDATE maintenance_snapshots SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM maintenance_snapshots")
    suspend fun deleteAll()
}
