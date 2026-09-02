package com.ronitgandhi.motionfuel.config

import com.google.firebase.FirebaseApp
import com.ronitgandhi.motionfuel.BuildConfig

// Centralises client-safe configuration. The Maps key is generated from the ignored
// secrets.properties file; Firebase readiness is detected at runtime from google-services.json.
object AppConfig {
    // The Google Maps key is safe for the APK (restricted by SHA-1 + package in the Cloud console)
    // and is consumed by the manifest meta-data and the maps-compose basemap.
    val mapsApiKey: String = BuildConfig.MAPS_API_KEY.trim()

    // Maps render on a licensed basemap only when a real key is present; otherwise the offline
    // RouteCanvas is used so the build always runs without a key.
    val isMapsConfigured: Boolean = mapsApiKey.isNotBlank() &&
        !mapsApiKey.contains("replace_me", ignoreCase = true)

    // Firebase Auth + Firestore are ready only when google-services.json was present at build time,
    // which causes the google-services plugin to register a default FirebaseApp at startup. Without
    // it the app still compiles and runs, showing the "configure Firebase" gate.
    val isFirebaseConfigured: Boolean
        get() {
            val context = appContextHolder ?: return false
            return FirebaseApp.getApps(context).isNotEmpty()
        }

    // MotionFuelApplication assigns the application Context here during onCreate so the runtime
    // FirebaseApp check needs no parameter at every call site.
    @Volatile
    var appContextHolder: android.content.Context? = null
}
