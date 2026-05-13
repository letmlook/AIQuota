package com.aiquota.app.api

import com.aiquota.app.model.QuotaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DeepSeekApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // DeepSeek 余额查询 API（正确端点）
    private val quotaUrl = "https://api.deepseek.com/user/balance"

    suspend fun queryQuota(apiKey: String): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(quotaUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)

                // DeepSeek 返回格式:
                // { "is_available": true, "balance_infos": [{ "currency": "CNY", "total_balance": "110.00", "granted_balance": "10.00", "topped_up_balance": "100.00" }] }
                val isAvailable = json.optBoolean("is_available", false)
                val balanceInfos = json.optJSONArray("balance_infos")

                var totalBalance = 0.0
                var grantedBalance = 0.0
                var toppedUpBalance = 0.0
                var currency = "CNY"

                if (balanceInfos != null && balanceInfos.length() > 0) {
                    val info = balanceInfos.getJSONObject(0)
                    totalBalance = info.optString("total_balance", "0").toDoubleOrNull() ?: 0.0
                    grantedBalance = info.optString("granted_balance", "0").toDoubleOrNull() ?: 0.0
                    toppedUpBalance = info.optString("topped_up_balance", "0").toDoubleOrNull() ?: 0.0
                    currency = info.optString("currency", "CNY")
                }

                // DeepSeek 按调用量计费，没有固定额度
                // 这里用余额来模拟：余额 -> tokens (1元 = 1000000 tokens)
                // 或者直接显示余额金额
                val remaining = (totalBalance * 1000000).toLong()

                return@withContext Result.success(
                    QuotaInfo(
                        provider = "DeepSeek",
                        planName = "余额套餐",
                        status = if (isAvailable) "正常" else "额度用尽",
                        isAvailable = isAvailable,
                        currency = currency,
                        used = 0L, // DeepSeek 不返回已使用量
                        total = 0L,
                        remaining = remaining,
                        grantedBalance = grantedBalance,
                        toppedUpBalance = toppedUpBalance,
                        usedFormatted = "-",
                        totalFormatted = if (currency == "CNY") "¥$totalBalance" else "$$totalBalance",
                        remainingFormatted = if (currency == "CNY") "¥${String.format("%.2f", totalBalance)}" else "$${String.format("%.2f", totalBalance)}",
                        usagePercent = 0, // DeepSeek 按量付费，无百分比
                        resetDate = ""
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
}
