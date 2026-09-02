package com.ronitgandhi.motionfuel.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ronitgandhi.motionfuel.config.AppConfig
import java.util.concurrent.TimeUnit

// Schedules network-constrained, deduplicated cloud sync passes for workouts and nutrition.
object SyncScheduler {
    private const val WORK_NAME = "motionfuel-sync"

    fun enqueue(context: Context) {
        // Does nothing until Firebase is configured; the signed-in check happens inside the worker.
        if (!AppConfig.isFirebaseConfigured) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE coalesces rapid saves into one queued drain instead of many.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
