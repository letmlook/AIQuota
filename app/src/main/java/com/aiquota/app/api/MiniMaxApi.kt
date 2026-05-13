package com.aiquota.app.api

import com.aiquota.app.model.MinimaxModelRemain
import com.aiquota.app.model.QuotaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class MiniMaxApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // MiniMax Token Plan 余额查询 API
    private val quotaUrl = "https://www.minimaxi.com/v1/token_plan/remains"

    // 存储最后一次原始返回，用于调试显示
    var lastRawResponse: String = ""
        private set

    suspend fun queryQuota(apiKey: String): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(quotaUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            lastRawResponse = body

            if (response.isSuccessful) {
                val json = JSONObject(body)

                // 检查 base_resp 状态码
                val baseResp = json.optJSONObject("base_resp")
                if (baseResp != null && baseResp.optInt("status_code", 0) != 0) {
                    val statusMsg = baseResp.optString("status_msg", "未知错误")
                    return@withContext Result.failure(Exception("API错误: $statusMsg"))
                }

                // 解析每个模型的限额
                val modelRemains = json.optJSONArray("model_remains")
                val modelList = mutableListOf<MinimaxModelRemain>()
                var totalUsage = 0L
                var totalQuota = 0L

                if (modelRemains != null && modelRemains.length() > 0) {
                    for (i in 0 until modelRemains.length()) {
                        val item = modelRemains.getJSONObject(i)
                        val modelName = item.optString("model_name", "unknown")
                        val total = item.optLong("current_interval_total_count", 0L)
                        val usage = item.optLong("current_interval_usage_count", 0L)

                        // 显示所有模型（不再跳过 total=0 的）
                        totalUsage += usage
                        totalQuota += total
                        val remaining = total - usage
                        val isUnlimited = total == 0L
                        modelList.add(
                            MinimaxModelRemain(
                                modelName = modelName,
                                totalCount = total,
                                usageCount = usage,
                                remainingCount = if (isUnlimited) 0 else remaining,
                                usagePercent = if (total > 0) ((usage.toDouble() / total) * 100).toInt() else 0,
                                isUnlimited = isUnlimited,
                                intervalStartTime = item.optLong("start_time", 0),
                                intervalEndTime = item.optLong("end_time", 0),
                                intervalRemainsTime = item.optLong("remains_time", 0),
                                weeklyStartTime = item.optLong("weekly_start_time", 0),
                                weeklyEndTime = item.optLong("weekly_end_time", 0),
                                weeklyRemainsTime = item.optLong("weekly_remains_time", 0),
                                weeklyTotalCount = item.optLong("current_weekly_total_count", 0),
                                weeklyUsageCount = item.optLong("current_weekly_usage_count", 0)
                            )
                        )
                    }
                }

                val remaining = totalQuota - totalUsage
                val isAvailable = totalQuota == 0L || remaining > 0L
                val usagePercent = if (totalQuota > 0L) ((totalUsage.toDouble() / totalQuota) * 100).toInt() else 0

                return@withContext Result.success(
                    QuotaInfo(
                        provider = "MiniMax",
                        planName = "Token Plan",
                        status = if (isAvailable) "正常" else "额度用尽",
                        isAvailable = isAvailable,
                        used = totalUsage,
                        total = totalQuota,
                        remaining = if (totalQuota == 0L) Long.MAX_VALUE else remaining,
                        usedFormatted = formatTokens(totalUsage),
                        totalFormatted = if (totalQuota == 0L) "∞" else formatTokens(totalQuota),
                        remainingFormatted = if (totalQuota == 0L) "∞" else formatTokens(remaining),
                        usagePercent = usagePercent,
                        resetDate = "",
                        minimaxModelRemains = modelList
                    )
                )
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(body)
                    errJson.optString("error", errJson.optString("message", "HTTP ${response.code}"))
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                Result.failure(Exception("请求失败: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatTokens(tokens: Long): String {
        return when {
            tokens >= 1_000_000_000 -> {
                val df = DecimalFormat("#.##")
                "${df.format(tokens / 1_000_000_000.0)} B"
            }
            tokens >= 1_000_000 -> {
                val df = DecimalFormat("#.##")
                "${df.format(tokens / 1_000_000.0)} M"
            }
            tokens >= 1_000 -> {
                val df = DecimalFormat("#.##")
                "${df.format(tokens / 1_000.0)} K"
            }
            else -> "$tokens"
        }
    }
}
