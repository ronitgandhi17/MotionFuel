import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Loads client-safe local configuration from the ignored root secrets.properties file.
val secrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) secretsFile.inputStream().use(::load)
}

// Reads a value from secrets.properties, Gradle properties, or environment variables in that order.
fun configurationValue(name: String): String =
    secrets.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: ""

// Escapes a configuration value so Gradle can safely generate a quoted BuildConfig string.
fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ronitgandhi.motionfuel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ronitgandhi.motionfuel"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Generates client-safe values consumed centrally by config/AppConfig.kt.
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", configurationValue("CLERK_PUBLISHABLE_KEY").asBuildConfigString())
        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", configurationValue("STRIPE_PUBLISHABLE_KEY").asBuildConfigString())
        buildConfigField("String", "MEMBERSHIP_API_BASE_URL", configurationValue("MEMBERSHIP_API_BASE_URL").asBuildConfigString())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Supplies Android core APIs, Compose activity hosting, lifecycle flows and ViewModels.
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    // Opens Stripe Customer Portal sessions in a secure in-app browser tab.
    implementation("androidx.browser:browser:1.10.0")

    // Supplies the Compose UI toolkit, Material 3 components and extended icons.
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.0")

    // Supplies offline Room persistence, KSP code generation and DataStore preferences.
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Runs deferrable, network-constrained cloud sync as a background WorkManager job.
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Adds the Kotlin-2.2-compatible Clerk SDK for authentication and session tokens.
    implementation("com.clerk:clerk-android:0.1.24")
    // Adds Stripe PaymentSheet and secure Android payment handling for memberships.
    implementation("com.stripe:stripe-android:23.15.0")

    // Supplies JUnit and coroutine testing support for local unit tests.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
