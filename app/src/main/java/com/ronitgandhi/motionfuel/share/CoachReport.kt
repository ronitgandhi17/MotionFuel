package com.ronitgandhi.motionfuel.share

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ronitgandhi.motionfuel.domain.model.*
import java.io.File
import java.time.Instant
import java.time.ZoneId

object CoachReport {
    fun create(context: Context, name: String, workouts: List<WorkoutSummary>, foods: List<NutritionEntry>, weights: List<WeightEntry>): Intent {
        val start = System.currentTimeMillis() - 28L * 86_400_000L
        val recent = workouts.filter { it.startedAtMillis >= start }
        val meals = foods.filter { it.consumedAtMillis >= start }
        val days = meals.groupBy { Instant.ofEpochMilli(it.consumedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate() }
        val lines = mutableListOf("MotionFuel — 28-day progress report", name.take(80), "Generated: ${java.time.LocalDate.now()}", "", "Workouts: ${recent.size}", "Distance: ${"%.2f".format(recent.sumOf { it.distanceMeters } / 1000)} km", "Active minutes: ${recent.sumOf { it.durationSeconds } / 60}", "", "Nutrition (recorded days only)", "Days logged: ${days.size}")
        if (days.isNotEmpty()) {
            lines += "Mean calories: ${(meals.sumOf { it.caloriesKcal } / days.size).toInt()} kcal/day"
            lines += "Mean protein: ${(meals.sumOf { it.proteinG } / days.size).toInt()} g/day"
        }
        lines += ""; lines += "Weight history"
        weights.filter { it.recordedAtMillis >= start }.sortedBy { it.recordedAtMillis }.forEach {
            lines += "${Instant.ofEpochMilli(it.recordedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()}: ${it.weightKg} kg"
        }
        lines += ""; lines += "Logged values and estimates; not a clinical assessment."
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "progress-${System.currentTimeMillis()}.pdf")
        PdfDocument().use { document ->
            lines.chunked(38).forEachIndexed { index, pageLines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
                pageLines.forEachIndexed { row, line -> page.canvas.drawText(line, 36f, 48f + row * 19f, paint) }
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        }
        return Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
