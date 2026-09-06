package com.ronitgandhi.motionfuel.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ronitgandhi.motionfuel.domain.model.HydrationEntry
import com.ronitgandhi.motionfuel.domain.model.NutritionEntry
import com.ronitgandhi.motionfuel.domain.model.WeightEntry
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object DataExport {
    fun createJsonShareIntent(
        context: Context,
        workouts: List<WorkoutSummary>,
        nutrition: List<NutritionEntry>,
        weights: List<WeightEntry>,
        hydration: List<HydrationEntry>,
    ): Intent {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach(File::delete)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val file = File(directory, "motionfuel-export-$stamp.json")
        val root = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("workouts", JSONArray().apply { workouts.forEach { workout -> put(JSONObject().apply {
                put("id", workout.id); put("type", workout.type.name); put("startedAt", workout.startedAtMillis)
                put("durationSeconds", workout.durationSeconds); put("distanceMeters", workout.distanceMeters)
                put("steps", workout.steps); put("caloriesKcal", workout.caloriesKcal)
            }) } })
            put("nutrition", JSONArray().apply { nutrition.forEach { entry -> put(JSONObject().apply {
                put("id", entry.id); put("name", entry.name); put("meal", entry.mealType.name); put("consumedAt", entry.consumedAtMillis)
                put("caloriesKcal", entry.caloriesKcal); put("proteinG", entry.proteinG); put("carbohydratesG", entry.carbohydratesG); put("fatG", entry.fatG)
            }) } })
            put("weights", JSONArray().apply { weights.forEach { put(JSONObject().put("weightKg", it.weightKg).put("recordedAt", it.recordedAtMillis)) } })
            put("hydration", JSONArray().apply { hydration.forEach { put(JSONObject().put("amountMl", it.amountMl).put("consumedAt", it.consumedAtMillis)) } })
        }
        file.writeText(root.toString(2))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MotionFuel data export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
