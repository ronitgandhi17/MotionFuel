package com.ronitgandhi.motionfuel.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RootSwipeConfigurationTest {
    @Test
    fun pagerSwipesAreDisabledForNestedFoodAndProfilePages() {
        val root = File("src/main/java/com/ronitgandhi/motionfuel/MainActivity.kt").readText()
        val food = File("src/main/java/com/ronitgandhi/motionfuel/ui/screens/FoodScreen.kt").readText()
        val profile = File("src/main/java/com/ronitgandhi/motionfuel/ui/screens/DashboardScreens.kt").readText()
        assertTrue(root.contains("userScrollEnabled = rootSwipeEnabled"))
        assertTrue(root.contains("MainTab.FOOD -> foodIsRootPage"))
        assertTrue(root.contains("MainTab.PROFILE -> profileIsRootPage"))
        assertTrue(food.contains("onRootPageChanged(selectedSavedFood == null)"))
        assertTrue(profile.contains("onRootPageChanged(!editing)"))
    }
}
