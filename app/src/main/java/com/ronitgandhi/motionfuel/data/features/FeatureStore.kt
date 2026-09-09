package com.ronitgandhi.motionfuel.data.features

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Extra preferences are scoped to the authenticated account, including drafts and food metadata. */
class FeatureStore(context: Context, uid: String) {
    private val preferences = context.getSharedPreferences("features_$uid", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(JSONObject(preferences.getString("data", "{}") ?: "{}"))
    val data = mutable.asStateFlow()
    @Synchronized fun update(block: (JSONObject) -> Unit) {
        val copy = JSONObject(mutable.value.toString())
        block(copy)
        check(preferences.edit().putString("data", copy.toString()).commit()) { "Could not save changes." }
        mutable.value = copy
    }
    fun array(key: String): List<JSONObject> {
        val a = mutable.value.optJSONArray(key) ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }
    fun putItem(key: String, item: JSONObject) = update { root ->
        val a = root.optJSONArray(key) ?: JSONArray()
        val rows = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("id") != item.getString("id") }
        root.put(key, JSONArray(rows + item))
    }
    fun removeItem(key: String, id: String) = update { root ->
        val a = root.optJSONArray(key) ?: JSONArray()
        root.put(key, JSONArray((0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("id") != id }))
    }
    fun replace(value: JSONObject) = update { old ->
        old.keys().asSequence().toList().forEach(old::remove)
        value.keys().forEach { old.put(it, value.get(it)) }
    }
}
