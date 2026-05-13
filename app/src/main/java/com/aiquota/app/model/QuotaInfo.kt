package com.aiquota.app.model

data class QuotaInfo(
    val provider: String,
    val planName: String,
    val status: String,
    val isAvailable: Boolean,
    val currency: String = "CNY",
    val used: Long,
    val total: Long,
    val remaining: Long,
    val grantedBalance: Double = 0.0,
    val toppedUpBalance: Double = 0.0,
    val usedFormatted: String,
    val totalFormatted: String,
    val remainingFormatted: String,
    val usagePercent: Int,
    val resetDate: String = "",
    /** DeepSeek 模型用量列表 */
    val modelUsages: List<ModelUsage> = emptyList(),
    /** MiniMax 模型限额列表 */
    val minimaxModelRemains: List<MinimaxModelRemain> = emptyList()
)

/**
 * DeepSeek 模型用量数据
 */
data class ModelUsage(
    val modelName: String,
    val usageCount: Long,
    val usageFormatted: String
)

/**
 * MiniMax 模型限额数据
 */
data class MinimaxModelRemain(
    val modelName: String,
    val totalCount: Long,
    val usageCount: Long,
    val remainingCount: Long,
    val usagePercent: Int,
    val isUnlimited: Boolean,
    // 本次周期（5小时）
    val intervalStartTime: Long = 0,
    val intervalEndTime: Long = 0,
    val intervalRemainsTime: Long = 0,
    // 本周
    val weeklyStartTime: Long = 0,
    val weeklyEndTime: Long = 0,
    val weeklyRemainsTime: Long = 0,
    val weeklyTotalCount: Long = 0,
    val weeklyUsageCount: Long = 0
)
