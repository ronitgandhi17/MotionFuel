package com.ronitgandhi.motionfuel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import com.ronitgandhi.motionfuel.data.features.FeatureStore
import com.ronitgandhi.motionfuel.domain.model.*
import com.ronitgandhi.motionfuel.ui.screens.EverydayToolsScreen
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PantryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pantryEntryCanBeAddedAndPersistsOutsideScreen() {
        val app = ApplicationProvider.getApplicationContext<MotionFuelApplication>()
        FeatureStore(app, "ui-test").replace(JSONObject())
        val profile = UserProfile("ui-test", "Tester", "tester@example.com", 24, BiologicalSex.MALE, 175.0, 72.0, ActivityLevel.MODERATE, 2400, 2200)
        val vm = MotionFuelViewModel(app)
        compose.setContent { MaterialTheme { EverydayToolsScreen(vm, profile) {} } }
        compose.onNodeWithText("Food name").performTextInput("Test rice")
        compose.onNodeWithText("Add to pantry").performScrollTo().performClick()
        compose.onNodeWithText("Test rice").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Test rice", FeatureStore(app, "ui-test").array("pantry").single().getString("name"))
        }
    }
}
