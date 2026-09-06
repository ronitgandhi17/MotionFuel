package com.ronitgandhi.motionfuel.data.network

import com.ronitgandhi.motionfuel.domain.model.FoodSearchResult
import com.ronitgandhi.motionfuel.domain.model.WeatherContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ContextApiClient {
    suspend fun currentWeather(latitude: Double, longitude: Double): Result<WeatherContext> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"
            val current = JSONObject(get(url)).getJSONObject("current")
            val code = current.optInt("weather_code", 0)
            WeatherContext(
                temperatureC = current.getDouble("temperature_2m"),
                humidityPercent = current.getInt("relative_humidity_2m"),
                windSpeedKph = current.getDouble("wind_speed_10m"),
                isRaining = code in 51..82,
            )
        }
    }

    suspend fun searchFoods(query: String): Result<List<FoodSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
            val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded" +
                "&search_simple=1&action=process&json=1&page_size=8&fields=code,product_name,brands,nutriments"
            val products = JSONObject(get(url)).getJSONArray("products")
            buildList {
                for (index in 0 until products.length()) {
                    val product = products.optJSONObject(index) ?: continue
                    val name = product.optString("product_name").takeIf { it.isNotBlank() } ?: continue
                    val nutrients = product.optJSONObject("nutriments") ?: JSONObject()
                    add(
                        FoodSearchResult(
                            providerId = product.optString("code", "food-$index"),
                            name = name,
                            brand = product.optString("brands").takeIf { it.isNotBlank() },
                            caloriesKcal = nutrients.optDouble("energy-kcal_100g", 0.0),
                            proteinG = nutrients.optDouble("proteins_100g", 0.0),
                            carbohydratesG = nutrients.optDouble("carbohydrates_100g", 0.0),
                            fatG = nutrients.optDouble("fat_100g", 0.0),
                        ),
                    )
                }
            }
        }
    }

    suspend fun foodByBarcode(barcode: String): Result<FoodSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(barcode.matches(Regex("[0-9]{8,14}"))) { "The scanned barcode is not a supported food code." }
            val root = JSONObject(get("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=code,product_name,brands,serving_size,nutriments"))
            require(root.optInt("status") == 1) { "No nutrition record was found for this barcode." }
            val product = root.getJSONObject("product")
            val nutrients = product.optJSONObject("nutriments") ?: JSONObject()
            val name = product.optString("product_name").takeIf { it.isNotBlank() } ?: "Scanned food"
            FoodSearchResult(
                providerId = product.optString("code", barcode),
                name = name,
                brand = product.optString("brands").takeIf { it.isNotBlank() },
                servingLabel = product.optString("serving_size").takeIf { it.isNotBlank() } ?: "100 g",
                caloriesKcal = nutrients.optDouble("energy-kcal_100g", 0.0),
                proteinG = nutrients.optDouble("proteins_100g", 0.0),
                carbohydratesG = nutrients.optDouble("carbohydrates_100g", 0.0),
                fatG = nutrients.optDouble("fat_100g", 0.0),
            )
        }
    }

    private fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MotionFuel university project/0.1")
            if (connection.responseCode !in 200..299) error("Remote service returned ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
