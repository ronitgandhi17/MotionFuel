package com.ronitgandhi.motionfuel.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.session.fetchToken
import com.ronitgandhi.motionfuel.MotionFuelApplication
import com.ronitgandhi.motionfuel.config.AppConfig
import kotlinx.coroutines.flow.first

// Drains the local pending sync queue to the backend and mirrors remote records into Room.
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MotionFuelApplication
        // Skips quietly when cloud sync is unconfigured or no Clerk session is signed in.
        if (!AppConfig.isSyncConfigured) return Result.success()
        val session = Clerk.session ?: return Result.success()
        val token = when (val result = session.fetchToken()) {
            is ClerkResult.Success -> result.value.jwt
            is ClerkResult.Failure -> return Result.retry()
        }
        return try {
            // Mirrors records saved on other devices before uploading local changes.
            val snapshot = app.syncApiClient.pull(token)
            app.repository.upsertRemoteWorkouts(snapshot.workouts)
            app.repository.upsertRemoteNutrition(snapshot.nutrition)
            // Uploads local pending rows; detailed routes upload only with explicit consent.
            val routeBackup = app.settingsRepository.settings.first().routeBackupEnabled
            val pendingWorkouts = app.repository.pendingWorkoutDtos(routeBackup)
            val pendingNutrition = app.repository.pendingNutritionDtos()
            if (pendingWorkouts.isNotEmpty() || pendingNutrition.isNotEmpty()) {
                app.syncApiClient.push(token, pendingWorkouts, pendingNutrition)
                app.repository.markWorkoutsSynced(pendingWorkouts.map { it.id })
                app.repository.markNutritionSynced(pendingNutrition.map { it.id })
            }
            Result.success()
        } catch (error: Exception) {
            // Network or backend hiccups retry under WorkManager's exponential backoff.
            Result.retry()
        }
    }
}
