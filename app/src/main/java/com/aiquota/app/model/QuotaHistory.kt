package com.aiquota.app.model

data class QuotaHistory(
    val id: Long = 0,
    val provider: String,
    val queryTime: Long,
    val planName: String,
    val status: String,
    val isAvailable: Boolean,
    val remaining: String,
    val remainingRaw: Long,
    val usagePercent: Int
)
