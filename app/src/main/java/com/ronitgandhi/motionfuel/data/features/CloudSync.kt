package com.ronitgandhi.motionfuel.data.features

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.ronitgandhi.motionfuel.MotionFuelApplication
import com.ronitgandhi.motionfuel.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Explicit snapshot synchronisation. Conflicting versions require a user's choice; never silently overwrite. */
class CloudSync(private val app: MotionFuelApplication, private val uid: String, private val features: FeatureStore) {
    private val prefs = app.getSharedPreferences("sync_$uid", Context.MODE_PRIVATE)
    private val binding = app.getSharedPreferences("dataset_owner", Context.MODE_PRIVATE)
    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        val tables = listOf("workouts", "nutrition_entries", "weight_entries", "saved_foods", "hydration_entries", "meal_plan_entries", "recipes", "planned_workouts", "planned_routes", "challenges")
    }
    private fun checkOwner() {
        check(FirebaseAuth.getInstance().currentUser?.let { it.uid == uid && it.isEmailVerified } == true) { "Sign in with a verified account first." }
        check(binding.getString("uid", null) == uid) { "Confirm that this device's records belong to your account before syncing." }
        check(com.ronitgandhi.motionfuel.service.WorkoutSessionController.telemetry.value.status == WorkoutStatus.IDLE) { "Finish the active workout before syncing." }
    }
    fun claimLocalData() {
        val existing = binding.getString("uid", null)
        check(existing == null || existing == uid) { "This device is bound to another account. Clear its local data before switching ownership." }
        check(binding.edit().putString("uid", uid).commit())
    }
    private suspend fun snapshot(): JSONObject = withContext(Dispatchers.IO) {
        val db = app.database.openHelper.readableDatabase
        val includeRoutes = features.data.value.optBoolean("syncRoutes", false)
        val root = JSONObject().put("schema", 6).put("owner", uid)
        val settings = app.settingsRepository.settings.first()
        root.put("settings", JSONObject().put("units", settings.units.name).put("dark", settings.darkTheme)
            .put("weight", settings.weightKg).put("weekly", settings.wellnessGoals.weeklyWorkoutTarget)
            .put("steps", settings.wellnessGoals.dailyStepTarget).put("water", settings.wellnessGoals.dailyWaterTargetMl)
            .put("language", settings.appLanguage).put("accessible", settings.accessibleDisplay))
        val extra = JSONObject(features.data.value.toString())
        // Diagnostic consent and device ownership must be chosen independently on each phone.
        extra.remove("diagnostics"); extra.remove("feedback")
        root.put("features", extra)
        db.beginTransaction()
        try {
            for (table in tables) {
                val rows = JSONArray()
                if (table != "planned_routes" || includeRoutes) db.query("SELECT * FROM `$table` ORDER BY id").use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        cursor.columnNames.forEachIndexed { index, column ->
                            val value: Any = when {
                                column == "photoUri" -> JSONObject.NULL
                                column == "routeJson" && !includeRoutes -> "[]"
                                column == "routeJson" && features.data.value.optBoolean("approximateCloudRoutes") -> {
                                    val route = JSONArray(cursor.getString(index))
                                    for (i in 0 until route.length()) {
                                        val point = route.getJSONObject(i)
                                        listOf("lat", "lon").forEach { key -> if (point.has(key)) point.put(key, kotlin.math.round(point.getDouble(key) * 1000) / 1000) }
                                    }
                                    route.toString()
                                }
                                cursor.isNull(index) -> JSONObject.NULL
                                cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                cursor.getType(index) == Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                                else -> cursor.getString(index)
                            }
                            row.put(column, value)
                        }
                        rows.put(row)
                    }
                }
                root.put(table, rows)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        root
    }
    private fun hash(value: JSONObject) = MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    suspend fun sync(choice: String? = null): String {
        checkOwner()
        val firestore = FirebaseFirestore.getInstance()
        val manifest = firestore.document("users/$uid/sync/current")
        val remote = manifest.get(Source.SERVER).await()
        val remoteVersion = remote.getString("version")
        val base = prefs.getString("version", null)
        val local = snapshot()
        val localHash = hash(local)
        val unchanged = localHash == prefs.getString("hash", null)
        if (choice == null && remoteVersion != null && remoteVersion != base && !unchanged) {
            return "CONFLICT"
        }
        if (choice == "download" || (choice == null && remoteVersion != null && remoteVersion != base && unchanged)) {
            check(remoteVersion != null) { "No cloud snapshot exists yet." }
            val bytes = FirebaseStorage.getInstance().reference.child("backups/$uid/$remoteVersion.json").getBytes(MAX_BYTES).await()
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            checkOwner()
            restore(root)
            val newHash = hash(snapshot())
            check(prefs.edit().putString("version", remoteVersion).putString("hash", newHash).commit())
            return "Cloud data restored. Local-only photos are not included."
        }
        if (unchanged && remoteVersion == base) return "Already synchronised."
        val bytes = local.toString().toByteArray()
        require(bytes.size <= MAX_BYTES) { "Snapshot exceeds 10 MB. Export your data locally instead." }
        val version = UUID.randomUUID().toString()
        val objectRef = FirebaseStorage.getInstance().reference.child("backups/$uid/$version.json")
        objectRef.putBytes(bytes, StorageMetadata.Builder().setContentType("application/json").build()).await()
        try {
            checkOwner()
            firestore.runTransaction { transaction ->
                check(transaction.get(manifest).getString("version") == remoteVersion) { "Another device synced meanwhile. Please retry." }
                transaction.set(manifest, mapOf("version" to version, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
        } catch (error: Exception) {
            runCatching { objectRef.delete().await() }
            throw error
        }
        check(prefs.edit().putString("version", version).putString("hash", localHash).commit())
        remoteVersion?.let { runCatching { FirebaseStorage.getInstance().reference.child("backups/$uid/$it.json").delete().await() } }
        return "Cloud snapshot saved. Sync on your other device to restore it."
    }
    private suspend fun restore(root: JSONObject) = withContext(Dispatchers.IO) {
        require(root.getInt("schema") == 6 && root.getString("owner") == uid) { "Snapshot account or schema mismatch." }
        val db = app.database.openHelper.writableDatabase
        val staged = tables.associateWith { table ->
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor -> while (cursor.moveToNext()) columns += cursor.getString(1) }
            val rows = root.getJSONArray(table)
            require(rows.length() <= 100_000)
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                require(row.keys().asSequence().toSet() == columns) { "Unexpected snapshot fields." }
                require(row.getString("id").length in 1..200)
                ContentValues().apply {
                    columns.forEach { column ->
                        val value = row.get(column)
                        when (value) {
                            JSONObject.NULL -> putNull(column)
                            is String -> put(column, value)
                            is Number -> { require(value.toDouble().isFinite()); if (value is Double || value is Float) put(column, value.toDouble()) else put(column, value.toLong()) }
                            else -> error("Invalid snapshot value")
                        }
                    }
                }
            }
        }
        val settings = root.getJSONObject("settings")
        val units = UnitSystem.valueOf(settings.getString("units"))
        require(settings.getDouble("weight").isFinite() && settings.getDouble("weight") in 30.0..350.0)
        require(settings.getInt("weekly") in 1..14 && settings.getInt("steps") in 1000..100000 && settings.getInt("water") in 500..8000)
        val extra = root.getJSONObject("features").put("diagnostics", features.data.value.optBoolean("diagnostics", false))
        checkOwner()
        db.beginTransaction()
        try {
            tables.forEach { table ->
                db.execSQL("DELETE FROM `$table`")
                staged.getValue(table).forEach { values -> db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        app.database.invalidationTracker.refreshAsync()
        features.replace(extra)
        app.settingsRepository.setUnits(units)
        app.settingsRepository.setDarkTheme(settings.getBoolean("dark"))
        app.settingsRepository.setWeight(settings.getDouble("weight"))
        app.settingsRepository.setWellnessGoals(WellnessGoals(settings.getInt("weekly"), settings.getInt("steps"), settings.getInt("water")))
        app.settingsRepository.setAppLanguage(settings.getString("language"))
        app.settingsRepository.setAccessibleDisplay(settings.getBoolean("accessible"))
    }
    suspend fun deleteCloudSnapshot(): String {
        checkOwner()
        val ref = FirebaseFirestore.getInstance().document("users/$uid/sync/current")
        val version = ref.get(Source.SERVER).await().getString("version")
        ref.delete().await()
        if (version != null) FirebaseStorage.getInstance().reference.child("backups/$uid/$version.json").delete().await()
        prefs.edit().clear().commit()
        return "Cloud snapshot deleted."
    }
}
