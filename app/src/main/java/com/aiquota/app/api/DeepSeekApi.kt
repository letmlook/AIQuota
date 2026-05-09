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

class DeepSeekApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // DeepSeek 余额查询 API
    private val quotaUrl = "https://api.deepseek.com/baichuan/user/balance"

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
                
                // DeepSeek 返回格式: { "is_available": true, "balance_infos": [{ "plan_name": "...", "total_balance": 10.0, "used_balance": 2.5 }] }
                val balanceInfos = json.optJSONArray("balance_infos")
                val balanceInfo = if (balanceInfos != null && balanceInfos.length() > 0) {
                    balanceInfos.getJSONObject(0)
                } else {
                    json
                }

                // 余额单位是人民币元，转换为tokens (1元 = 1000000 tokens)
                val totalBalance = balanceInfo.optDouble("total_balance", 0.0)
                val usedBalance = balanceInfo.optDouble("used_balance", 0.0)
                
                val total = (totalBalance * 1000000).toLong()
                val used = (usedBalance * 1000000).toLong()
                val remaining = total - used

                Result.success(
                    QuotaInfo(
                        planName = balanceInfo.optString("plan_name", "标准套餐"),
                        status = if (remaining > 0) "正常" else "额度用尽",
                        used = used,
                        total = total,
                        remaining = remaining,
                        usedFormatted = "¥${String.format("%.2f", usedBalance)}",
                        totalFormatted = "¥${String.format("%.2f", totalBalance)}",
                        remainingFormatted = "¥${String.format("%.2f", remaining / 1000000.0)}",
                        usagePercent = if (total > 0) ((used.toDouble() / total) * 100).toInt() else 0,
                        resetDate = ""
                    )
                )
            } else {
                val errorMsg = try { 
                    val errJson = JSONObject(body)
                    errJson.optString("error", errJson.optString("message", "未知错误"))
                } catch (e: Exception) { "HTTP ${response.code}" }
                Result.failure(Exception("请求失败 ($response.code): $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
