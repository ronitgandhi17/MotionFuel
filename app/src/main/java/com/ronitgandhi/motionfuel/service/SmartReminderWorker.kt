package com.ronitgandhi.motionfuel.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.ronitgandhi.motionfuel.R
import java.util.concurrent.TimeUnit

class SmartReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Wellness reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val message = when (hour) { in 8..10 -> "Plan breakfast and water for a steady start."; in 11..15 -> "Check your hydration and take a short movement break."; else -> "Review today’s meals and recovery before tomorrow." }
        manager.notify(4102, NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("MotionFuel check-in").setContentText(message).setAutoCancel(true).build())
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "smart_wellness"
        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) manager.cancelUniqueWork("motionfuel-smart-reminders") else manager.enqueueUniquePeriodicWork("motionfuel-smart-reminders", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<SmartReminderWorker>(6, TimeUnit.HOURS).build())
        }
    }
}

class WorkoutReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("workout_plans", "Workout plans", NotificationManager.IMPORTANCE_HIGH))
        manager.notify(inputData.getString("id").hashCode(), NotificationCompat.Builder(applicationContext, "workout_plans").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Planned workout").setContentText(inputData.getString("message") ?: "Your workout is ready to start.").setAutoCancel(true).build())
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, id: String, atMillis: Long, message: String) {
            val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<WorkoutReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(workDataOf("id" to id, "message" to message)).build()
            WorkManager.getInstance(context).enqueueUniqueWork("workout-plan-$id", androidx.work.ExistingWorkPolicy.REPLACE, request)
        }
    }
}
