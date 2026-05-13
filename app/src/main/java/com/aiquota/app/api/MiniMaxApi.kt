package com.aiquota.app.api

import com.aiquota.app.model.QuotaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class MiniMaxApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // MiniMax 余额查询 API（正确端点）
    private val quotaUrl = "https://www.minimaxi.com/v1/token_plan/remains"

    suspend fun queryQuota(apiKey: String): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        try {
            val requestBody = "{}".toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(quotaUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)

                // 错误检查
                if (json.has("error")) {
                    val errorMsg = json.optString("error", "未知错误")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                // MiniMax 返回格式:
                // { "code": 0, "data": { "total_amount": "...", "used_amount": "...", "remain_amount": "..." } }
                val data = json.optJSONObject("data") ?: json

                val total = data.optString("total_amount", "0").toLongOrNull() ?: 0L
                val used = data.optString("used_amount", "0").toLongOrNull() ?: 0L
                val remaining = data.optString("remain_amount", "0").toLongOrNull() ?: (total - used)

                return@withContext Result.success(
                    QuotaInfo(
                        provider = "MiniMax",
                        planName = data.optString("plan_name", "标准套餐"),
                        status = if (remaining > 0) "正常" else "额度用尽",
                        isAvailable = remaining > 0,
                        used = used,
                        total = total,
                        remaining = remaining,
                        usedFormatted = formatTokens(used),
                        totalFormatted = formatTokens(total),
                        remainingFormatted = formatTokens(remaining),
                        usagePercent = if (total > 0) ((used.toDouble() / total) * 100).toInt() else 0,
                        resetDate = data.optString("reset_date", "")
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
