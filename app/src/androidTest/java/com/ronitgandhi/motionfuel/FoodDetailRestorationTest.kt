package com.ronitgandhi.motionfuel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import com.ronitgandhi.motionfuel.domain.model.NutritionTotals
import com.ronitgandhi.motionfuel.domain.model.SavedFood
import com.ronitgandhi.motionfuel.ui.screens.FoodScreen
import org.junit.Rule
import org.junit.Test

class FoodDetailRestorationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectedFoodSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val food = SavedFood("rice", "Test rice", 100.0, 2.0, 22.0, 1.0, null, 1000L)
        restoration.setContent {
            MaterialTheme {
                FoodScreen(
                    darkTheme = false, totals = NutritionTotals(), entries = emptyList(), savedFoods = listOf(food),
                    results = emptyList(), searchStatus = null, onSearch = {}, onAddFood = { _, _ -> },
                    onAddManual = { _, _, _, _, _, _, _ -> }, onAddSavedFood = { _, _ -> },
                    onDeleteNutritionEntry = {}, onDeleteSavedFood = {}, tomorrowPlan = emptyList(), onBarcode = {},
                    onPlanFood = { _, _ -> }, onRemovePlannedFood = {}, onAddPlanToToday = {}, onRootPageChanged = {},
                )
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Test rice"))
        compose.onNodeWithText("Test rice").performClick()
        compose.onNodeWithText("Add to meal").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Add to meal").performScrollTo().assertIsDisplayed()
    }
}
