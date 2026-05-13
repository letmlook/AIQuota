package com.aiquota.app.api

import com.aiquota.app.model.ModelUsage
import com.aiquota.app.model.QuotaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class DeepSeekApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // DeepSeek 余额查询 API
    private val quotaUrl = "https://api.deepseek.com/user/balance"
    // DeepSeek 用量查询 API（返回每个模型的用量数据）
    private val usageUrl = "https://api.deepseek.com/user/tokens"

    // 存储最后一次原始返回，用于调试显示
    var lastRawResponse: String = ""
        private set

    private var lastUsageRawResponse: String = ""
        private set

    /**
     * 查询 DeepSeek 余额 + 用量数据
     */
    suspend fun queryQuota(apiKey: String): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        try {
            // 先获取余额
            val balanceRequest = Request.Builder()
                .url(quotaUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val balanceResponse = client.newCall(balanceRequest).execute()
            val balanceBody = balanceResponse.body?.string() ?: ""

            // 先获取用量数据（会设置 lastUsageRawResponse）
            val modelUsages = queryModelUsage(apiKey)
            // 组合原始返回用于调试
            lastRawResponse = buildString {
                appendLine("=== 余额查询 ===")
                appendLine(balanceBody)
                appendLine()
                appendLine("=== 用量查询 ===")
                appendLine(lastUsageRawResponse)
            }

            if (!balanceResponse.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(balanceBody)
                    errJson.optString("error", errJson.optString("message", "HTTP ${balanceResponse.code}"))
                } catch (e: Exception) {
                    "HTTP ${balanceResponse.code}"
                }
                return@withContext Result.failure(Exception("请求失败: $errorMsg"))
            }

            val balanceJson = JSONObject(balanceBody)
            val isAvailable = balanceJson.optBoolean("is_available", false)
            val balanceInfos = balanceJson.optJSONArray("balance_infos")

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

            val remaining = (totalBalance * 1000000).toLong()

            return@withContext Result.success(
                QuotaInfo(
                    provider = "DeepSeek",
                    planName = "余额套餐",
                    status = if (isAvailable) "正常" else "额度用尽",
                    isAvailable = isAvailable,
                    currency = currency,
                    used = 0L,
                    total = 0L,
                    remaining = remaining,
                    grantedBalance = grantedBalance,
                    toppedUpBalance = toppedUpBalance,
                    usedFormatted = "-",
                    totalFormatted = if (currency == "CNY") "¥$totalBalance" else "$$totalBalance",
                    remainingFormatted = if (currency == "CNY") "¥${String.format("%.2f", totalBalance)}" else "$${String.format("%.2f", totalBalance)}",
                    usagePercent = 0,
                    resetDate = "",
                    modelUsages = modelUsages
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 查询 DeepSeek 模型用量
     * 返回格式: { "data": [{ "model": "deepseek-chat", "total_usage": 1234567 }] }
     */
    private suspend fun queryModelUsage(apiKey: String): List<ModelUsage> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(usageUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            lastUsageRawResponse = body // 保存原始返回用于调试

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val dataArray = json.optJSONArray("data") ?: return@withContext emptyList()

                val usages = mutableListOf<ModelUsage>()
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val modelName = item.optString("model", "unknown")
                    val totalUsage = item.optLong("total_usage", 0L)

                    if (modelName.isNotEmpty() && totalUsage > 0) {
                        usages.add(
                            ModelUsage(
                                modelName = modelName,
                                usageCount = totalUsage,
                                usageFormatted = formatTokens(totalUsage)
                            )
                        )
                    }
                }
                usages
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
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
