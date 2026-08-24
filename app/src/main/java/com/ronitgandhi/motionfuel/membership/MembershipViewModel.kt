package com.ronitgandhi.motionfuel.membership

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.session.fetchToken
import com.ronitgandhi.motionfuel.MotionFuelApplication
import com.ronitgandhi.motionfuel.config.AppConfig
import com.ronitgandhi.motionfuel.data.network.SubscriptionPayment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MembershipUiState(
    val configured: Boolean,
    val busy: Boolean = false,
    val status: String = "free",
    val statusLabel: String = "Free membership",
    val detail: String = "Core workout and nutrition tracking",
    val message: String? = null,
    val payment: SubscriptionPayment? = null,
    val portalUrl: String? = null,
) {
    val isPro: Boolean get() = status == "active" || status == "trialing"
    val canManageBilling: Boolean get() = status in setOf("active", "trialing", "past_due", "incomplete")
}

class MembershipViewModel(application: Application) : AndroidViewModel(application) {
    // Reuses the application-level API client for every membership request.
    private val client = (application as MotionFuelApplication).membershipApiClient
    private val isConfigured = AppConfig.isStripeConfigured
    private val mutableState = MutableStateFlow(MembershipUiState(configured = isConfigured))
    val state = mutableState.asStateFlow()

    init {
        // Loads the Stripe subscription status when the signed-in profile starts.
        if (isConfigured) refresh()
    }

    // Retrieves the current Stripe membership using a fresh Clerk session token.
    fun refresh() {
        if (!ensureConfigured()) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { client.membership(clerkToken()) }
                .onSuccess { snapshot ->
                    val period = snapshot.currentPeriodEndMillis?.let {
                        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it))
                    }
                    val label = when (snapshot.status) {
                        "active" -> "MotionFuel Pro"
                        "trialing" -> "MotionFuel Pro trial"
                        "past_due" -> "Payment needs attention"
                        "canceled" -> "Membership canceled"
                        else -> "Free membership"
                    }
                    val detail = when {
                        period != null && snapshot.status == "active" -> "Renews $period"
                        period != null && snapshot.status == "trialing" -> "Trial ends $period"
                        snapshot.status == "past_due" -> "Open billing to update your payment method"
                        else -> "Core workout and nutrition tracking"
                    }
                    mutableState.update {
                        it.copy(
                            busy = false,
                            status = snapshot.status,
                            statusLabel = label,
                            detail = detail,
                        )
                    }
                }
                .onFailure(::showFailure)
        }
    }

    // Creates an incomplete Stripe subscription before opening PaymentSheet.
    fun startSubscription() {
        if (!ensureConfigured()) return
        if (mutableState.value.isPro) {
            mutableState.update { it.copy(message = "Your Pro membership is already active.") }
            return
        }
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { client.createSubscription(clerkToken()) }
                .onSuccess { payment -> mutableState.update { it.copy(busy = false, payment = payment) } }
                .onFailure(::showFailure)
        }
    }

    // Requests a short-lived Stripe Customer Portal URL from the secure server.
    fun openBillingPortal() {
        if (!ensureConfigured()) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { client.createPortalSession(clerkToken()) }
                .onSuccess { url -> mutableState.update { it.copy(busy = false, portalUrl = url) } }
                .onFailure(::showFailure)
        }
    }

    // Clears consumed PaymentSheet data so checkout is not opened twice.
    fun paymentSheetPresented() {
        mutableState.update { it.copy(payment = null) }
    }

    // Clears the consumed portal URL after Android hands it to the browser.
    fun portalOpened() {
        mutableState.update { it.copy(portalUrl = null) }
    }

    // Waits briefly and refreshes Stripe after PaymentSheet submits the first payment.
    fun paymentCompleted() {
        mutableState.update {
            it.copy(message = "Payment submitted. Membership will activate after Stripe confirms it.")
        }
        viewModelScope.launch {
            delay(1_500)
            refresh()
        }
    }

    // Displays PaymentSheet or browser failures inside the membership card.
    fun paymentFailed(message: String) {
        mutableState.update { it.copy(message = message) }
    }

    // Prevents billing actions until all client-safe Stripe configuration is present.
    private fun ensureConfigured(): Boolean {
        if (isConfigured) return true
        mutableState.update {
            it.copy(
                message = "Add STRIPE_PUBLISHABLE_KEY and MEMBERSHIP_API_BASE_URL to secrets.properties.",
            )
        }
        return false
    }

    // Fetches a fresh Clerk JWT that the membership server verifies before billing.
    private suspend fun clerkToken(): String {
        val session = Clerk.session ?: error("Sign in again before managing membership.")
        return when (val result = session.fetchToken()) {
            is ClerkResult.Success -> result.value.jwt
            is ClerkResult.Failure -> throw result.throwable
                ?: IllegalStateException("Clerk could not issue a session token.")
        }
    }

    // Converts membership network failures into a user-readable state message.
    private fun showFailure(throwable: Throwable) {
        mutableState.update {
            it.copy(
                busy = false,
                message = throwable.localizedMessage ?: "Membership could not be updated.",
            )
        }
    }
}
