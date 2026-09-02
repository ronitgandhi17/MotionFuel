package com.ronitgandhi.motionfuel.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import com.ronitgandhi.motionfuel.domain.model.UnitSystem
import com.ronitgandhi.motionfuel.domain.model.WorkoutSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

object ActivityShareImage {
    private const val Width = 1080
    private const val Height = 1350

    // Renders a social-media-ready activity card and exposes it through a temporary content URI.
    fun createShareIntent(
        context: Context,
        workout: WorkoutSummary,
        units: UnitSystem,
        darkTheme: Boolean,
        mapBitmap: Bitmap? = null,
    ): Intent {
        val bitmap = render(workout, units, darkTheme, mapBitmap)
        val directory = File(context.cacheDir, "shared_activities").apply { mkdirs() }
        directory.listFiles()?.filter { it.name.endsWith(".png") }?.forEach(File::delete)
        val file = File(directory, "motionfuel-${workout.id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My ${workout.type.name.lowercase()} activity on MotionFuel")
            clipData = ClipData.newRawUri("MotionFuel activity", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun render(workout: WorkoutSummary, units: UnitSystem, dark: Boolean, mapBitmap: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = if (dark) Color.rgb(8, 19, 34) else Color.rgb(246, 247, 249)
        val foreground = if (dark) Color.rgb(228, 236, 248) else Color.rgb(20, 32, 51)
        val secondary = if (dark) Color.rgb(185, 198, 216) else Color.rgb(69, 82, 79)
        val panel = if (dark) Color.rgb(21, 41, 71) else Color.WHITE
        val accent = Color.rgb(86, 227, 159)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.textSize = 34f
        paint.color = accent
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MOTIONFUEL", 64f, 76f, paint)
        paint.textSize = 64f
        paint.color = foreground
        canvas.drawText(workout.type.name.lowercase().replaceFirstChar(Char::uppercase), 64f, 154f, paint)
        paint.textSize = 27f
        paint.color = secondary
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(SimpleDateFormat("EEEE, d MMMM yyyy • h:mm a", Locale.getDefault()).format(Date(workout.startedAtMillis)), 64f, 202f, paint)

        val routePanel = RectF(64f, 238f, 1016f, 818f)
        paint.color = panel
        canvas.drawRoundRect(routePanel, 42f, 42f, paint)
        if (mapBitmap == null) {
            drawRoute(canvas, paint, workout.route, routePanel, accent, secondary)
        } else {
            // Clips the live Google map snapshot into the social card without covering its attribution.
            val checkpoint = canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(routePanel, 42f, 42f, Path.Direction.CW) })
            canvas.drawBitmap(mapBitmap, Rect(0, 0, mapBitmap.width, mapBitmap.height), routePanel, paint)
            canvas.restoreToCount(checkpoint)
        }

        val imperial = units == UnitSystem.IMPERIAL
        val paceSeconds = workout.averagePaceSecPerKm?.let { if (imperial) it * 1.609344 else it }
        drawMetric(canvas, paint, 64f, 914f, "DISTANCE", distance(workout.distanceMeters, imperial), foreground, secondary)
        drawMetric(canvas, paint, 566f, 914f, "MOVING TIME", duration(workout.durationSeconds), foreground, secondary)
        drawMetric(canvas, paint, 64f, 1060f, "AVERAGE PACE", "${pace(paceSeconds)} /${if (imperial) "mi" else "km"}", foreground, secondary)
        drawMetric(canvas, paint, 566f, 1060f, "ENERGY", "${workout.caloriesKcal.toInt()} kcal", foreground, secondary)
        drawMetric(canvas, paint, 64f, 1206f, "STEPS", "%d".format(Locale.US, workout.steps), foreground, secondary)
        drawMetric(canvas, paint, 566f, 1206f, "ELEVATION", "${workout.elevationGainMeters.toInt()} m", foreground, secondary)
        return bitmap
    }

    private fun drawRoute(canvas: Canvas, paint: Paint, route: List<GeoPoint>, area: RectF, accent: Int, secondary: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(35, Color.red(secondary), Color.green(secondary), Color.blue(secondary))
        repeat(4) { index ->
            val x = area.left + area.width() * (index + 1) / 5f
            val y = area.top + area.height() * (index + 1) / 5f
            canvas.drawLine(x, area.top + 30f, x, area.bottom - 30f, paint)
            canvas.drawLine(area.left + 30f, y, area.right - 30f, y, paint)
        }
        if (route.size < 2) {
            paint.style = Paint.Style.FILL
            paint.textSize = 30f
            paint.color = secondary
            canvas.drawText("Route outline unavailable", area.left + 310f, area.centerY(), paint)
            return
        }
        val meanLatitude = route.map { it.latitude }.average()
        val projected = route.map { (it.longitude * cos(Math.toRadians(meanLatitude))) to it.latitude }
        val minX = projected.minOf { it.first }
        val maxX = projected.maxOf { it.first }
        val minY = projected.minOf { it.second }
        val maxY = projected.maxOf { it.second }
        val xRange = max(maxX - minX, 0.00001)
        val yRange = max(maxY - minY, 0.00001)
        val padding = 72f
        val path = Path()
        projected.forEachIndexed { index, point ->
            val x = area.left + padding + ((point.first - minX) / xRange * (area.width() - padding * 2)).toFloat()
            val y = area.bottom - padding - ((point.second - minY) / yRange * (area.height() - padding * 2)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 15f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = accent
        canvas.drawPath(path, paint)
    }

    private fun drawMetric(canvas: Canvas, paint: Paint, x: Float, y: Float, label: String, value: String, foreground: Int, secondary: Int) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 25f
        paint.color = secondary
        canvas.drawText(label, x, y, paint)
        paint.textSize = 47f
        paint.color = foreground
        canvas.drawText(value, x, y + 58f, paint)
    }

    private fun duration(seconds: Long) = String.format(Locale.US, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
    private fun pace(seconds: Double?) = if (seconds == null || !seconds.isFinite()) "—" else String.format(Locale.US, "%d:%02d", (seconds / 60).toInt(), seconds.toInt() % 60)
    private fun distance(meters: Double, imperial: Boolean) = if (imperial) String.format(Locale.US, "%.2f mi", meters / 1_609.344) else String.format(Locale.US, "%.2f km", meters / 1_000.0)
}
