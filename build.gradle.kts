plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
    // Processes google-services.json into Firebase configuration. Applied conditionally by :app
    // so an unconfigured checkout (no google-services.json) still builds and runs.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
