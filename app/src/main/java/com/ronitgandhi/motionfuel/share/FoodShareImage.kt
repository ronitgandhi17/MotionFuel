package com.ronitgandhi.motionfuel.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.ronitgandhi.motionfuel.domain.model.SavedFood
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object FoodShareImage {
    private const val Width = 1080
    private const val Height = 1350
    private const val RetentionMillis = 24L * 60L * 60L * 1_000L

    // Removes food-share cards after one day so temporary images do not accumulate.
    fun deleteExpiredFiles(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        File(context.cacheDir, "shared_foods").listFiles()
            ?.filter { it.isFile && nowMillis - it.lastModified() >= RetentionMillis }
            ?.forEach(File::delete)
    }

    // Renders the saved food as a social card and grants temporary access through FileProvider.
    fun createShareIntent(context: Context, food: SavedFood, darkTheme: Boolean): Intent {
        val photo = food.photoUri?.let { loadPhoto(context, Uri.parse(it)) }
        val bitmap = render(food, darkTheme, photo)
        val directory = File(context.cacheDir, "shared_foods").apply { mkdirs() }
        directory.listFiles()?.filter { it.name.endsWith(".png") }?.forEach(File::delete)
        val file = File(directory, "motionfuel-food-${food.id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${food.name} on MotionFuel")
            clipData = ClipData.newRawUri("MotionFuel food card", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun loadPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largestSide = max(info.size.width, info.size.height)
            if (largestSide > 1_600) {
                val scale = 1_600f / largestSide
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    }.getOrNull()

    private fun render(food: SavedFood, dark: Boolean, photo: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = if (dark) Color.rgb(8, 19, 34) else Color.rgb(246, 247, 249)
        val foreground = if (dark) Color.rgb(228, 236, 248) else Color.rgb(20, 32, 51)
        val secondary = if (dark) Color.rgb(185, 198, 216) else Color.rgb(69, 82, 79)
        val panel = if (dark) Color.rgb(21, 41, 71) else Color.WHITE
        val accent = Color.rgb(86, 227, 159)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(background)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 34f
        paint.color = accent
        canvas.drawText("MOTIONFUEL", 64f, 76f, paint)
        paint.textSize = 62f
        paint.color = foreground
        canvas.drawText(fitText(food.name, paint, 952f), 64f, 154f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 27f
        paint.color = secondary
        canvas.drawText("MY SAVED FOOD", 64f, 202f, paint)

        val photoPanel = RectF(64f, 238f, 1016f, 842f)
        paint.color = panel
        canvas.drawRoundRect(photoPanel, 42f, 42f, paint)
        if (photo == null) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 38f
            paint.color = secondary
            val message = "Food photo unavailable"
            canvas.drawText(message, photoPanel.centerX() - paint.measureText(message) / 2f, photoPanel.centerY(), paint)
        } else {
            drawCenterCrop(canvas, paint, photo, photoPanel)
        }

        drawMetric(canvas, paint, 64f, 930f, "CALORIES", "${food.caloriesKcal.toInt()} kcal", foreground, secondary, 54f)
        drawMetric(canvas, paint, 64f, 1080f, "CARBOHYDRATES", "${food.carbohydratesG.toInt()} g", foreground, secondary, 43f)
        drawMetric(canvas, paint, 398f, 1080f, "PROTEIN", "${food.proteinG.toInt()} g", foreground, secondary, 43f)
        drawMetric(canvas, paint, 732f, 1080f, "FAT", "${food.fatG.toInt()} g", foreground, secondary, 43f)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 25f
        paint.color = secondary
        canvas.drawText("Fuel your progress with MotionFuel", 64f, 1284f, paint)
        return bitmap
    }

    private fun drawCenterCrop(canvas: Canvas, paint: Paint, bitmap: Bitmap, area: RectF) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = area.width() / area.height()
        val source = if (sourceRatio > targetRatio) {
            val width = (bitmap.height * targetRatio).toInt()
            val left = (bitmap.width - width) / 2
            Rect(left, 0, left + width, bitmap.height)
        } else {
            val height = (bitmap.width / targetRatio).toInt()
            val top = (bitmap.height - height) / 2
            Rect(0, top, bitmap.width, top + height)
        }
        val checkpoint = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(area, 42f, 42f, Path.Direction.CW) })
        canvas.drawBitmap(bitmap, source, area, paint)
        canvas.restoreToCount(checkpoint)
    }

    private fun drawMetric(canvas: Canvas, paint: Paint, x: Float, y: Float, label: String, value: String, foreground: Int, secondary: Int, valueSize: Float) {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        paint.color = secondary
        canvas.drawText(label, x, y, paint)
        paint.textSize = valueSize
        paint.color = foreground
        canvas.drawText(value, x, y + 58f, paint)
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        val count = paint.breakText(text, true, maxWidth - paint.measureText(suffix), null)
        return text.take(min(count, text.length)) + suffix
    }
}
