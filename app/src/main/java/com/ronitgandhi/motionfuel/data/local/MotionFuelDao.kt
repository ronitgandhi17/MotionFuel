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

    @Query("DELETE FROM nutrition_entries")
    suspend fun deleteAll()
}
