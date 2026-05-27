package com.aiquota.app.api

import com.aiquota.app.model.QuotaInfo
import com.aiquota.app.model.VolcPlanUsage
import com.aiquota.app.model.VolcSubscribeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class VolcEngineApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://console.volcengine.com/api/top/ark/cn-beijing/2024-01-01"
    private val jsonMediaType = "application/json".toMediaType()

    var lastRawResponse: String = ""
        private set

    suspend fun queryQuota(digest: String, csrfToken: String, accountId: String): Result<QuotaInfo> =
        withContext(Dispatchers.IO) {
            try {
                // 1. GetCodingPlanUsage
                val usageResult = getCodingPlanUsage(digest, csrfToken)
                if (usageResult.isFailure) return@withContext Result.failure(usageResult.exceptionOrNull()!!)

                // 2. ListSubscribeTrade
                val subscribeResult = listSubscribeTrade(digest, csrfToken)
                if (subscribeResult.isFailure) return@withContext Result.failure(subscribeResult.exceptionOrNull()!!)

                val usages = usageResult.getOrDefault(emptyList())
                val subscribeInfo = subscribeResult.getOrDefault(null)

                val monthlyUsage = usages.find { it.level == "monthly" }
                val usagePercent = monthlyUsage?.percent?.toInt() ?: 0
                val isAvailable = monthlyUsage == null || monthlyUsage.percent < 100.0

                val planLabel = when (subscribeInfo?.bizInfo) {
                    "lite" -> "Coding Plan Lite"
                    "pro" -> "Coding Plan Pro"
                    else -> "Coding Plan"
                }

                Result.success(
                    QuotaInfo(
                        provider = "VolcEngine",
                        planName = planLabel,
                        status = if (subscribeInfo?.status == "Running") "运行中" else "未订阅",
                        isAvailable = isAvailable,
                        used = usagePercent.toLong(),
                        total = 100,
                        remaining = (100 - usagePercent).toLong(),
                        usedFormatted = "$usagePercent%",
                        totalFormatted = "100%",
                        remainingFormatted = "${100 - usagePercent}%",
                        usagePercent = usagePercent,
                        resetDate = subscribeInfo?.endTime ?: "",
                        volcPlanUsages = usages,
                        volcSubscribeInfo = subscribeInfo
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun getCodingPlanUsage(digest: String, csrfToken: String): Result<List<VolcPlanUsage>> {
        return try {
            val body = "{}"
            val request = buildPostRequest("$baseUrl/GetCodingPlanUsage?", body, digest, csrfToken)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            lastRawResponse = responseBody

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val result = json.optJSONObject("Result") ?: return Result.failure(Exception("无Result数据"))
            val quotaUsage = result.optJSONArray("QuotaUsage") ?: return Result.success(emptyList())

            val usages = mutableListOf<VolcPlanUsage>()
            for (i in 0 until quotaUsage.length()) {
                val item = quotaUsage.getJSONObject(i)
                val level = item.optString("Level", "")
                val percent = item.optDouble("Percent", 0.0)
                val resetTs = item.optLong("ResetTimestamp", 0)

                val levelLabel = when (level) {
                    "session" -> "近5小时"
                    "weekly" -> "本周"
                    "monthly" -> "本月"
                    else -> level
                }
                val remaining = 100.0 - percent
                val resetFormatted = formatResetTime(resetTs)

                usages.add(
                    VolcPlanUsage(
                        level = level,
                        percent = percent,
                        resetTimestamp = resetTs,
                        levelLabel = levelLabel,
                        remainingPercent = remaining,
                        resetTimeFormatted = resetFormatted
                    )
                )
            }
            Result.success(usages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun listSubscribeTrade(digest: String, csrfToken: String): Result<VolcSubscribeInfo?> {
        return try {
            val body = """{"ResourceTypes":["CodingPlan"],"ResourceNames":[""],"BizInfos":["lite","pro"]}"""
            val request = buildPostRequest("$baseUrl/ListSubscribeTrade?", body, digest, csrfToken)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(Exception("ListSubscribeTrade HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val result = json.optJSONObject("Result") ?: return Result.success(null)
            val infoList = result.optJSONArray("InfoList") ?: return Result.success(null)

            if (infoList.length() == 0) return Result.success(null)

            val item = infoList.getJSONObject(0)
            Result.success(
                VolcSubscribeInfo(
                    bizInfo = item.optString("BizInfo", ""),
                    status = item.optString("Status", ""),
                    startTime = item.optString("StartTime", ""),
                    endTime = item.optString("EndTime", ""),
                    autoRenew = item.optBoolean("EnableAutoRenew", false),
                    period = item.optString("Period", "")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPostRequest(url: String, body: String, digest: String, csrfToken: String): Request {
        val decodedDigest = try { URLDecoder.decode(digest, "UTF-8") } catch (e: Exception) { digest }
        val decodedCsrf = try { URLDecoder.decode(csrfToken, "UTF-8") } catch (e: Exception) { csrfToken }
        return Request.Builder()
            .url(url)
            .addHeader("Cookie", "digest=$decodedDigest; csrfToken=$decodedCsrf")
            .addHeader("x-csrf-token", csrfToken)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Origin", "https://console.volcengine.com")
            .addHeader("Referer", "https://console.volcengine.com/ark/")
            .addHeader("Accept-Language", "zh")
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    private fun formatResetTime(timestampSeconds: Long): String {
        if (timestampSeconds <= 0) return ""
        val now = System.currentTimeMillis() / 1000
        val diffSeconds = timestampSeconds - now
        if (diffSeconds <= 0) return "即将刷新"

        val days = diffSeconds / 86400
        val hours = (diffSeconds % 86400) / 3600
        val minutes = (diffSeconds % 3600) / 60

        return when {
            days > 0 -> "${days}天${hours}时后刷新"
            hours > 0 -> "${hours}时${minutes}分后刷新"
            else -> "${minutes}分后刷新"
        }
    }

    fun formatEndTime(isoTime: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(isoTime) ?: return isoTime
            val output = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            output.format(date)
        } catch (e: Exception) {
            isoTime
        }
    }
}
