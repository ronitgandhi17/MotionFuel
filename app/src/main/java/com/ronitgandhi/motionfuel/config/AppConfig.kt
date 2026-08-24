package com.ronitgandhi.motionfuel.config

import com.ronitgandhi.motionfuel.BuildConfig

// Centralises client-safe configuration generated from the ignored secrets.properties file.
object AppConfig {
    // Clerk publishable keys are safe for the APK and identify the Clerk application.
    val clerkPublishableKey: String = BuildConfig.CLERK_PUBLISHABLE_KEY.trim()

    // Stripe publishable keys are safe for the APK and initialise Stripe PaymentSheet.
    val stripePublishableKey: String = BuildConfig.STRIPE_PUBLISHABLE_KEY.trim()

    // The membership API URL points to the HTTPS server that safely holds all secret keys.
    val membershipApiBaseUrl: String = BuildConfig.MEMBERSHIP_API_BASE_URL.trim().trimEnd('/')

    // This prevents placeholder values from being treated as a configured Clerk key.
    val isClerkConfigured: Boolean = clerkPublishableKey.startsWith("pk_") &&
        !clerkPublishableKey.contains("replace_me", ignoreCase = true)

    // Stripe is ready only when both its publishable key and the secure server URL are present.
    val isStripeConfigured: Boolean = stripePublishableKey.startsWith("pk_") &&
        !stripePublishableKey.contains("replace_me", ignoreCase = true) &&
        membershipApiBaseUrl.startsWith("https://") &&
        !membershipApiBaseUrl.contains("example.com", ignoreCase = true)
}
