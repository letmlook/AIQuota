package com.aiquota.app.data

import android.content.Context
import android.content.SharedPreferences
import com.aiquota.app.model.QuotaHistory
import org.json.JSONArray
import org.json.JSONObject

class HistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("quota_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "query_history"
        private const val MAX_HISTORY = 100
    }

    fun addRecord(
        provider: String,
        planName: String,
        status: String,
        isAvailable: Boolean,
        remaining: String,
        remainingRaw: Long,
        usagePercent: Int
    ) {
        val history = getHistory().toMutableList()

        val record = QuotaHistory(
            id = System.currentTimeMillis(),
            provider = provider,
            queryTime = System.currentTimeMillis(),
            planName = planName,
            status = status,
            isAvailable = isAvailable,
            remaining = remaining,
            remainingRaw = remainingRaw,
            usagePercent = usagePercent
        )

        history.add(0, record) // 添加到最新

        // 限制数量
        val trimmed = history.take(MAX_HISTORY)
        saveHistory(trimmed)
    }

    fun getHistory(): List<QuotaHistory> {
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                QuotaHistory(
                    id = obj.optLong("id", 0),
                    provider = obj.optString("provider", ""),
                    queryTime = obj.optLong("queryTime", 0),
                    planName = obj.optString("planName", ""),
                    status = obj.optString("status", ""),
                    isAvailable = obj.optBoolean("isAvailable", false),
                    remaining = obj.optString("remaining", ""),
                    remainingRaw = obj.optLong("remainingRaw", 0),
                    usagePercent = obj.optInt("usagePercent", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getHistoryByProvider(provider: String): List<QuotaHistory> {
        return getHistory().filter { it.provider == provider }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(history: List<QuotaHistory>) {
        val array = JSONArray()
        history.forEach { record ->
            val obj = JSONObject().apply {
                put("id", record.id)
                put("provider", record.provider)
                put("queryTime", record.queryTime)
                put("planName", record.planName)
                put("status", record.status)
                put("isAvailable", record.isAvailable)
                put("remaining", record.remaining)
                put("remainingRaw", record.remainingRaw)
                put("usagePercent", record.usagePercent)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
