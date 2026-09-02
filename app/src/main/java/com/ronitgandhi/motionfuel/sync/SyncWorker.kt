package com.ronitgandhi.motionfuel.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ronitgandhi.motionfuel.MotionFuelApplication
import com.ronitgandhi.motionfuel.config.AppConfig
import kotlinx.coroutines.flow.first

// Drains the local PENDING queue directly to the signed-in user's Firestore scope with the client
// SDK, then mirrors remote records back into Room. No token fetch is needed — the Firebase client
// carries the authenticated session.
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MotionFuelApplication
        // Skips quietly when Firebase is unconfigured or nobody is signed in.
        if (!AppConfig.isFirebaseConfigured) return Result.success()
        val uid = app.authRepository.uid ?: return Result.success()
        val gateway = app.firestoreGateway
        if (!gateway.isAvailable) return Result.success()
        val repo = app.repository

        return try {
            // Pull remote first so other-device changes land locally as SYNCED.
            val snapshot = gateway.pull(uid)
            repo.upsertRemoteWorkouts(snapshot.workouts)
            repo.upsertRemoteNutrition(snapshot.nutrition)
            repo.upsertRemoteWeights(snapshot.weights)
            repo.upsertRemoteCustomMeals(snapshot.customMeals)
            repo.upsertRemoteDailySummaries(snapshot.dailySummaries)
            repo.upsertRemoteMaintenance(snapshot.maintenance)
            snapshot.profile?.let { remote ->
                // Only adopt the remote profile when we have no local one yet (e.g. fresh reinstall).
                if (repo.currentProfile() == null) repo.upsertRemoteProfile(remote)
            }

            // Detailed routes upload only with explicit backup consent.
            val routeBackup = app.settingsRepository.settings.first().routeBackupEnabled

            val profiles = repo.pendingProfiles()
            profiles.forEach { gateway.pushProfile(uid, it) }
            repo.markProfilesSynced(profiles.map { it.uid })

            val workouts = repo.pendingWorkouts()
            workouts.forEach { gateway.pushWorkout(uid, it, routeBackup) }
            repo.markWorkoutsSynced(workouts.map { it.id })

            val nutrition = repo.pendingNutrition()
            nutrition.forEach { gateway.pushNutrition(uid, it) }
            repo.markNutritionSynced(nutrition.map { it.id })

            val weights = repo.pendingWeights()
            weights.forEach { gateway.pushWeight(uid, it) }
            repo.markWeightsSynced(weights.map { it.id })

            val meals = repo.pendingCustomMeals()
            meals.forEach { gateway.pushCustomMeal(uid, it) }
            repo.markCustomMealsSynced(meals.map { it.id })

            val summaries = repo.pendingDailySummaries()
            summaries.forEach { gateway.pushDailySummary(uid, it) }
            repo.markDailySummariesSynced(summaries.map { it.dateKey })

            val maintenance = repo.pendingMaintenance()
            maintenance.forEach { gateway.pushMaintenance(uid, it) }
            repo.markMaintenanceSynced(maintenance.map { it.id })

            Result.success()
        } catch (error: Exception) {
            // Network or backend hiccups retry under WorkManager's exponential backoff.
            Result.retry()
        }
    }
}
