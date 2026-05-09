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

    // MiniMax 可能可用的 API 端点
    private val quotaUrls = listOf(
        "https://api.minimaxi.com/group/balance",
        "https://api.minimaxi.com/v1/group/balance"
    )

    suspend fun queryQuota(apiKey: String): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        
        for (quotaUrl in quotaUrls) {
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
                    
                    // MiniMax 返回格式可能是: { "code": 0, "data": { "total_amount": ..., "used_amount": ... } }
                    if (json.has("error") || json.has("message")) {
                        val errorMsg = json.optString("error", json.optString("message", "未知错误"))
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                    
                    val data = if (json.has("data")) json.getJSONObject("data") else json

                    val total = data.optDouble("total_amount", 0.0).toLong()
                    val used = data.optDouble("used_amount", 0.0).toLong()
                    val remaining = total - used

                    return@withContext Result.success(
                        QuotaInfo(
                            planName = data.optString("plan_name", data.optString("planName", "标准套餐")),
                            status = data.optString("status", if (remaining > 0) "正常" else "额度用尽"),
                            used = used,
                            total = total,
                            remaining = remaining,
                            usedFormatted = formatTokens(used),
                            totalFormatted = formatTokens(total),
                            remainingFormatted = formatTokens(remaining),
                            usagePercent = if (total > 0) ((used.toDouble() / total) * 100).toInt() else 0,
                            resetDate = data.optString("reset_date", data.optString("resetDate", ""))
                        )
                    )
                } else {
                    lastError = Exception("请求失败 (HTTP ${response.code}): $body")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        
        // 所有端点都失败了，返回错误
        Result.failure(lastError ?: Exception("MiniMax 余额查询 API 暂不可用，请确认 API Key 是否正确"))
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
