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
    fun fileProviderGrantsOnlyTheDedicatedShareCacheDirectory() {
        val paths = appFile("res/xml/file_paths.xml")
        assertTrue(paths.contains("<cache-path"))
        assertTrue(paths.contains("path=\"shared_activities/\""))
        assertFalse(paths.contains("path=\".\""))
        assertFalse(paths.contains("<root-path"))
        assertFalse(paths.contains("<external-path"))
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
        assertTrue(rules.contains("match /{document=**}"))
        assertTrue(rules.contains("allow read, write: if false"))
    }

    @Test
    fun productionSourcesContainNoRecognisablePrivateKeyLiterals() {
        val sources = File("src/main").walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(Regex("AIza[0-9A-Za-z_-]{35}").containsMatchIn(sources))
        assertFalse(Regex("sk_(?:live|test)_[0-9A-Za-z]{16,}").containsMatchIn(sources))
        assertFalse(sources.contains("BEGIN PRIVATE KEY"))
    }
}
