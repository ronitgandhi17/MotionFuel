package com.ronitgandhi.motionfuel.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationTest {
    private fun appFile(path: String) = File("src/main/$path").readText()

    @Test
    fun manifestDisablesBackupAndCleartextTraffic() {
        val manifest = appFile("AndroidManifest.xml")
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    }

    @Test
    fun serviceAndFileProviderAreNotExported() {
        val manifest = appFile("AndroidManifest.xml")
        val privateComponentCount = Regex("android:exported=\"false\"").findAll(manifest).count()
        assertTrue(privateComponentCount >= 2)
    }

    @Test
    fun fileProviderGrantsOnlyDedicatedPrivateShareAndFoodDirectories() {
        val paths = appFile("res/xml/file_paths.xml")
        assertTrue(paths.contains("<cache-path"))
        assertTrue(paths.contains("path=\"shared_activities/\""))
        assertTrue(paths.contains("<files-path"))
        assertTrue(paths.contains("path=\"food_photos/\""))
        assertFalse(paths.contains("path=\".\""))
        assertFalse(paths.contains("<root-path"))
        assertFalse(paths.contains("<external-path"))
    }

    @Test
    fun foodPhotoUsesCameraContractWithoutBroadCameraOrMediaPermission() {
        val manifest = appFile("AndroidManifest.xml")
        val screen = appFile("java/com/ronitgandhi/motionfuel/ui/screens/FoodScreen.kt")
        assertTrue(screen.contains("ActivityResultContracts.TakePicture()"))
        assertFalse(screen.contains("ActivityResultContracts.OpenDocument()"))
        assertFalse(manifest.contains("android.permission.CAMERA"))
        assertFalse(manifest.contains("android.permission.READ_MEDIA_IMAGES"))
    }

    @Test
    fun networkSecurityConfigurationRejectsCleartext() {
        val config = appFile("res/xml/network_security_config.xml")
        assertTrue(config.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(config.contains("cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun firestoreRulesDenyUnauthenticatedFallbackAccess() {
        val rules = File("../firestore.rules").readText()
        assertTrue(rules.contains("request.auth != null"))
        assertTrue(rules.contains("request.auth.uid == uid"))
        assertTrue(rules.contains("request.auth.token.email_verified == true"))
        assertTrue(rules.contains("data.keys().hasOnly"))
        assertTrue(rules.contains("match /{document=**}"))
        assertTrue(rules.contains("allow read, write: if false"))
    }

    @Test
    fun backupRulesExcludeRoomAndDataStoreFromTransfer() {
        val rules = appFile("res/xml/data_extraction_rules.xml")
        assertTrue(rules.contains("domain=\"file\" path=\"datastore/motionfuel.preferences_pb\""))
        assertFalse(rules.contains("domain=\"sharedpref\" path=\"motionfuel.preferences_pb\""))
        assertFalse(rules.contains("domain=\"root\""))
        assertFalse(rules.contains("domain=\"external\""))
    }

    @Test
    fun appCheckProvidersAreVariantSpecific() {
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("debugImplementation(\"com.google.firebase:firebase-appcheck-debug\")"))
        assertTrue(build.contains("releaseImplementation(\"com.google.firebase:firebase-appcheck-playintegrity\")"))
    }

    @Test
    fun authenticationRequiresVerifiedEmailAndRefreshesTheToken() {
        val source = appFile("java/com/ronitgandhi/motionfuel/auth/FirebaseAuthViewModel.kt")
        assertTrue(source.contains("if (!user.isEmailVerified)"))
        assertTrue(source.contains("AuthLifecycle.EMAIL_VERIFICATION_REQUIRED"))
        assertTrue(source.contains("getIdToken(true).await()"))
    }

    @Test
    fun workoutNotificationHidesDetailsOnTheLockScreen() {
        val source = appFile("java/com/ronitgandhi/motionfuel/service/WorkoutTrackingService.kt")
        assertTrue(source.contains("setVisibility(NotificationCompat.VISIBILITY_PRIVATE)"))
        assertTrue(source.contains("setPublicVersion"))
    }

    @Test
    fun completeRouteSharingRequiresConfirmationAndExpiresCachedImages() {
        val screen = appFile("java/com/ronitgandhi/motionfuel/ui/screens/ActivityDetailScreen.kt")
        val renderer = appFile("java/com/ronitgandhi/motionfuel/share/ActivityShareImage.kt")
        assertTrue(screen.contains("Share complete route?"))
        assertTrue(screen.contains("start and finish locations"))
        assertTrue(renderer.contains("RetentionMillis"))
        assertTrue(renderer.contains("deleteExpiredFiles"))
    }

    @Test
    fun productionSourcesContainNoRecognisablePrivateKeyLiterals() {
        val sources = File("src/main").walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(Regex("AIza[0-9A-Za-z_-]{35}").containsMatchIn(sources))
        assertFalse(Regex("sk_(?:live|test)_[0-9A-Za-z]{16,}").containsMatchIn(sources))
        assertFalse(sources.contains("BEGIN PRIVATE KEY"))
    }
}
