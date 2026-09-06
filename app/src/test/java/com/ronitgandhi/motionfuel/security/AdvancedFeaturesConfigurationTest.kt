package com.ronitgandhi.motionfuel.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedFeaturesConfigurationTest {
    private fun appFile(path: String) = File("src/main/$path").readText()

    @Test
    fun advancedFeatureEntryPointsArePresent() {
        val vm = appFile("java/com/ronitgandhi/motionfuel/MotionFuelViewModel.kt")
        val dashboard = appFile("java/com/ronitgandhi/motionfuel/ui/screens/DashboardScreens.kt")
        val advanced = appFile("java/com/ronitgandhi/motionfuel/ui/screens/AdvancedFeatureCards.kt")
        val food = appFile("java/com/ronitgandhi/motionfuel/ui/screens/FoodScreen.kt")
        val activity = appFile("java/com/ronitgandhi/motionfuel/ui/screens/ActivityDetailScreen.kt")
        assertTrue(vm.contains("adaptiveFuelTarget"))
        assertTrue(advanced.contains("Health Connect"))
        assertTrue(food.contains("GmsBarcodeScanning"))
        assertTrue(activity.contains("Kilometre splits"))
        assertTrue(vm.contains("addWater"))
        assertTrue(advanced.contains("Recovery readiness"))
        assertTrue(food.contains("Tomorrow's meal plan"))
        assertTrue(advanced.contains("Achievements:"))
        assertTrue(advanced.contains("Home-screen widget"))
        assertTrue(advanced.contains("Export my data"))
        assertTrue(advanced.contains("Weekly insight report"))
        assertTrue(dashboard.contains("ConnectedToolsScreen"))
    }

    @Test
    fun integrationsUsePlatformAndPlayServicesDependencies() {
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("androidx.health.connect:connect-client"))
        assertTrue(build.contains("play-services-code-scanner"))
        assertFalse(build.contains("play-services-wearable"))
        assertTrue(build.contains("text-recognition"))
        assertTrue(build.contains("work-runtime-ktx"))
    }

    @Test
    fun wearOsCompanionIsNotIncluded() {
        val vm = appFile("java/com/ronitgandhi/motionfuel/MotionFuelViewModel.kt")
        val advanced = appFile("java/com/ronitgandhi/motionfuel/ui/screens/AdvancedFeatureCards.kt")
        val settings = appFile("java/com/ronitgandhi/motionfuel/data/settings/SettingsRepository.kt")
        assertFalse(vm.contains("WearableBridge"))
        assertFalse(vm.contains("sendWearableCommand"))
        assertFalse(advanced.contains("Wear OS sync"))
        assertFalse(settings.contains("wearable_sync_enabled"))
    }

    @Test
    fun healthPermissionsAreReadOnlyAndOptional() {
        val manifest = appFile("AndroidManifest.xml")
        val manager = appFile("java/com/ronitgandhi/motionfuel/integration/HealthConnectManager.kt")
        val screen = appFile("java/com/ronitgandhi/motionfuel/ui/screens/AdvancedFeatureCards.kt")
        assertTrue(manifest.contains("android.permission.health.READ_STEPS"))
        assertTrue(manifest.contains("android.permission.health.READ_SLEEP"))
        assertTrue(manifest.contains("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"))
        assertTrue(manifest.contains("android.intent.action.VIEW_PERMISSION_USAGE"))
        assertFalse(manifest.contains("android.permission.health.WRITE_"))
        assertTrue(manager.contains("SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED"))
        assertTrue(manager.contains("play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
        assertTrue(manager.contains("ACTION_HEALTH_CONNECT_SETTINGS"))
        assertTrue(screen.contains("Open Health Connect setup"))
    }

    @Test
    fun widgetReceiverIsNarrowlyScoped() {
        val manifest = appFile("AndroidManifest.xml")
        assertTrue(manifest.contains(".widget.MotionFuelWidgetProvider"))
        assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertFalse(manifest.contains("android:exported=\"true\" android:permission=\"\""))
    }

    @Test
    fun exportUsesPrivateCacheAndContentUri() {
        val paths = appFile("res/xml/file_paths.xml")
        val export = appFile("java/com/ronitgandhi/motionfuel/share/DataExport.kt")
        assertTrue(paths.contains("path=\"exports/\""))
        assertTrue(export.contains("FileProvider.getUriForFile"))
        assertTrue(export.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        assertFalse(export.contains("Environment.getExternalStorage"))
    }

    @Test
    fun databaseMigrationCreatesExpansionTables() {
        val app = appFile("java/com/ronitgandhi/motionfuel/MotionFuelApplication.kt")
        val db = appFile("java/com/ronitgandhi/motionfuel/data/local/MotionFuelDatabase.kt")
        assertTrue(db.contains("version = 6"))
        assertTrue(app.contains("MIGRATION_5_6"))
        assertTrue(app.contains("recipes"))
        assertTrue(app.contains("planned_workouts"))
        assertTrue(app.contains("planned_routes"))
        assertTrue(app.contains("challenges"))
    }

    @Test
    fun expansionFeaturesHaveSecuredPlatformEntryPoints() {
        val screen = appFile("java/com/ronitgandhi/motionfuel/ui/screens/ExpansionHubScreen.kt")
        val manifest = appFile("AndroidManifest.xml")
        val paths = appFile("res/xml/file_paths.xml")
        listOf("Smart meal recommendations", "Scan nutrition label", "Recipe builder", "Workout planner", "Route planner", "Live safety sharing", "Exercise-zone analysis", "Progress prediction", "Challenges and leaderboard", "Personal-best celebrations", "Intelligent notifications", "offline routes", "Data-source management", "Accessibility and localisation").forEach { assertTrue(it, screen.contains(it, ignoreCase = true)) }
        assertTrue(manifest.contains("android:scheme=\"motionfuel\""))
        assertTrue(paths.contains("path=\"label_scans/\""))
    }

    @Test
    fun accountDeletionRequiresRecentLoginBeforeCloudMutation() {
        val auth = appFile("java/com/ronitgandhi/motionfuel/auth/FirebaseAuthViewModel.kt")
        val guard = auth.indexOf("lastSignInTimestamp")
        val storageDelete = auth.indexOf("profile-images/${'$'}uid/avatar", startIndex = guard)
        assertTrue(guard >= 0)
        assertTrue(storageDelete > guard)
        assertTrue(auth.contains("weightDocuments.documents.chunked(400)"))
    }
}
