package com.ronitgandhi.motionfuel.data.network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SubscriptionPayment(
    val subscriptionId: String,
    val clientSecret: String,
    val ephemeralKey: String,
    val customerId: String,
)

data class MembershipSnapshot(
    val status: String,
    val currentPeriodEndMillis: Long? = null,
)

class MembershipApiClient(private val baseUrl: String) {
    // Reads the server-verified Stripe subscription status for the signed-in Clerk user.
    suspend fun membership(token: String): MembershipSnapshot = withContext(Dispatchers.IO) {
        val json = request("/billing/membership", "GET", token)
        MembershipSnapshot(
            status = json.optString("status", "free"),
            currentPeriodEndMillis = json.optLong("currentPeriodEnd", 0L)
                .takeIf { it > 0L }
                ?.times(1_000L),
        )
    }

    // Creates the server-side subscription and returns only short-lived PaymentSheet values.
    suspend fun createSubscription(token: String): SubscriptionPayment = withContext(Dispatchers.IO) {
        val json = request(
            path = "/billing/subscription",
            method = "POST",
            token = token,
            body = JSONObject().put("plan", "pro_monthly"),
        )
        SubscriptionPayment(
            subscriptionId = json.getString("subscriptionId"),
            clientSecret = json.getString("clientSecret"),
            ephemeralKey = json.getString("ephemeralKey"),
            customerId = json.getString("customerId"),
        )
    }

    // Requests a temporary Customer Portal URL without exposing the Stripe secret key.
    suspend fun createPortalSession(token: String): String = withContext(Dispatchers.IO) {
        request("/billing/portal", "POST", token, JSONObject()).getString("url")
    }

    // Sends an authenticated HTTPS JSON request and converts unsuccessful responses into errors.
    private fun request(
        path: String,
        method: String,
        token: String,
        body: JSONObject? = null,
    ): JSONObject {
        check(baseUrl.startsWith("https://")) {
            "MEMBERSHIP_API_BASE_URL must be a secure HTTPS URL."
        }
        // Adds the Clerk JWT as a bearer token so the server can identify the customer safely.
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            // Writes JSON only for POST requests that provide a request body.
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            // Reads either the success body or the server's structured error response.
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = response.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
            if (code !in 200..299) {
                throw IllegalStateException(json.optString("error", "Membership service returned HTTP $code."))
            }
            return json
        } finally {
            // Releases the HTTP connection after every success or failure.
            connection.disconnect()
        }
    }
}
