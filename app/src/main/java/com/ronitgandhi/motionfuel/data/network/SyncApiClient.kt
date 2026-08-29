package com.ronitgandhi.motionfuel.data.network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Mirrors one Room workout row for transport to the Clerk-verified sync backend.
data class WorkoutSyncDto(
    val id: String,
    val type: String,
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val averagePaceSecPerKm: Double?,
    val steps: Long,
    val elevationGainMeters: Double,
    val caloriesKcal: Double,
    val dominantActivity: String,
    val rejectedGpsPoints: Int,
    val routeJson: String,
)

// Mirrors one Room nutrition row for transport to the sync backend.
data class NutritionSyncDto(
    val id: String,
    val name: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
    val mealType: String,
    val consumedAtMillis: Long,
    val createdOffline: Boolean,
)

// The records the backend returns for the signed-in user, ready to mirror into Room.
data class RemoteSnapshot(
    val workouts: List<WorkoutSyncDto>,
    val nutrition: List<NutritionSyncDto>,
)

// Sends and receives Firestore-backed data through the secure Clerk-verified backend gateway.
class SyncApiClient(private val baseUrl: String) {
    // Uploads locally pending rows so the backend persists them under the verified Clerk user.
    suspend fun push(token: String, workouts: List<WorkoutSyncDto>, nutrition: List<NutritionSyncDto>) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("workouts", JSONArray().apply { workouts.forEach { put(it.toJson()) } })
            put("nutritionEntries", JSONArray().apply { nutrition.forEach { put(it.toJson()) } })
        }
        request("/sync/push", "POST", token, body)
        Unit
    }

    // Downloads the caller's cloud records so Room can mirror data saved on other devices.
    suspend fun pull(token: String): RemoteSnapshot = withContext(Dispatchers.IO) {
        val json = request("/sync/pull", "GET", token)
        val workouts = json.optJSONArray("workouts") ?: JSONArray()
        val nutrition = json.optJSONArray("nutritionEntries") ?: JSONArray()
        RemoteSnapshot(
            workouts = List(workouts.length()) { workoutFromJson(workouts.getJSONObject(it)) },
            nutrition = List(nutrition.length()) { nutritionFromJson(nutrition.getJSONObject(it)) },
        )
    }

    private fun WorkoutSyncDto.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("startedAtMillis", startedAtMillis)
        put("durationSeconds", durationSeconds)
        put("distanceMeters", distanceMeters)
        if (averagePaceSecPerKm != null) put("averagePaceSecPerKm", averagePaceSecPerKm)
        put("steps", steps)
        put("elevationGainMeters", elevationGainMeters)
        put("caloriesKcal", caloriesKcal)
        put("dominantActivity", dominantActivity)
        put("rejectedGpsPoints", rejectedGpsPoints)
        // Route travels as a structured array so Firestore documents stay clean and queryable.
        put("route", runCatching { JSONArray(routeJson) }.getOrDefault(JSONArray()))
    }

    private fun workoutFromJson(json: JSONObject): WorkoutSyncDto = WorkoutSyncDto(
        id = json.getString("id"),
        type = json.optString("type", "RUN"),
        startedAtMillis = json.optLong("startedAtMillis"),
        durationSeconds = json.optLong("durationSeconds"),
        distanceMeters = json.optDouble("distanceMeters", 0.0),
        averagePaceSecPerKm = if (json.has("averagePaceSecPerKm") && !json.isNull("averagePaceSecPerKm")) json.getDouble("averagePaceSecPerKm") else null,
        steps = json.optLong("steps"),
        elevationGainMeters = json.optDouble("elevationGainMeters", 0.0),
        caloriesKcal = json.optDouble("caloriesKcal", 0.0),
        dominantActivity = json.optString("dominantActivity", "UNKNOWN"),
        rejectedGpsPoints = json.optInt("rejectedGpsPoints", 0),
        // Preserves the structured route array back into the string shape Room already stores.
        routeJson = (json.optJSONArray("route") ?: JSONArray()).toString(),
    )

    private fun NutritionSyncDto.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("caloriesKcal", caloriesKcal)
        put("proteinG", proteinG)
        put("carbohydratesG", carbohydratesG)
        put("fatG", fatG)
        put("mealType", mealType)
        put("consumedAtMillis", consumedAtMillis)
        put("createdOffline", createdOffline)
    }

    private fun nutritionFromJson(json: JSONObject): NutritionSyncDto = NutritionSyncDto(
        id = json.getString("id"),
        name = json.optString("name", "Food"),
        caloriesKcal = json.optDouble("caloriesKcal", 0.0),
        proteinG = json.optDouble("proteinG", 0.0),
        carbohydratesG = json.optDouble("carbohydratesG", 0.0),
        fatG = json.optDouble("fatG", 0.0),
        mealType = json.optString("mealType", "SNACK"),
        consumedAtMillis = json.optLong("consumedAtMillis"),
        createdOffline = json.optBoolean("createdOffline", false),
    )

    // Sends an authenticated HTTPS JSON request, mirroring MembershipApiClient's bearer-token contract.
    private fun request(path: String, method: String, token: String, body: JSONObject? = null): JSONObject {
        check(baseUrl.startsWith("https://")) { "MEMBERSHIP_API_BASE_URL must be a secure HTTPS URL." }
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = response.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
            if (code !in 200..299) {
                throw IllegalStateException(json.optString("error", "Sync service returned HTTP $code."))
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}
