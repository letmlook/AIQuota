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
    val resetDate: String = ""
)
