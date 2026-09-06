package com.ronitgandhi.motionfuel.integration

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ronitgandhi.motionfuel.domain.algorithm.ExpansionEngine
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import kotlinx.coroutines.tasks.await

class NutritionLabelScanner(private val context: Context) {
    suspend fun scan(uri: Uri): Result<NutritionTotals> = runCatching {
        val image = InputImage.fromFilePath(context, uri)
        val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
        ExpansionEngine.parseNutritionLabel(text).also {
            require(it.caloriesKcal > 0 || it.proteinG > 0 || it.carbohydratesG > 0 || it.fatG > 0) { "No nutrition values were recognised. Retake the photo in bright, even light." }
        }
    }
}
