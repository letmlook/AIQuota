package com.aiquota.app.model

data class QuotaInfo(
    val planName: String,
    val status: String,
    val used: Long,
    val total: Long,
    val remaining: Long,
    val usedFormatted: String,
    val totalFormatted: String,
    val remainingFormatted: String,
    val usagePercent: Int,
    val resetDate: String
)
