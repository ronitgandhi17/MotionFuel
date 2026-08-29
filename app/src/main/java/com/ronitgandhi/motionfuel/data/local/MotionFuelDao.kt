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

    // Reads rows still awaiting upload so the sync worker can push them to the backend.
    @Query("SELECT * FROM workouts WHERE syncState = 'PENDING' ORDER BY startedAtMillis ASC")
    suspend fun pending(): List<WorkoutEntity>

    // Mirrors rows pulled from the backend; they arrive already marked as synced.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WorkoutEntity>)

    // Flips uploaded rows to synced so they are not sent again.
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

    // Reads rows still awaiting upload so the sync worker can push them to the backend.
    @Query("SELECT * FROM nutrition_entries WHERE syncState = 'PENDING' ORDER BY consumedAtMillis ASC")
    suspend fun pending(): List<NutritionEntryEntity>

    // Mirrors rows pulled from the backend; they arrive already marked as synced.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NutritionEntryEntity>)

    // Flips uploaded rows to synced so they are not sent again.
    @Query("UPDATE nutrition_entries SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM nutrition_entries")
    suspend fun deleteAll()
}
