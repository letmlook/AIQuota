package com.aiquota.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiquota.app.R
import com.aiquota.app.model.MinimaxModelRemain
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ModelUsageAdapter(
    private val items: List<MinimaxModelRemain>
) : RecyclerView.Adapter<ModelUsageAdapter.ViewHolder>() {

    // Tab 状态: 0 = 本次周期(5小时), 1 = 本周
    var showInterval: Int = 0

    private val df = DecimalFormat("#,###")
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModelName: TextView = view.findViewById(R.id.tvModelName)
        val tvCurrentUsage: TextView = view.findViewById(R.id.tvCurrentUsage)
        val tvTimeRange: TextView = view.findViewById(R.id.tvTimeRange)
        val tvResetTime: TextView = view.findViewById(R.id.tvResetTime)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val tvPercent: TextView = view.findViewById(R.id.tvPercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isInterval = showInterval == 0

        holder.tvModelName.text = item.modelName

        if (isInterval) {
            // 本次周期（5小时）
            val startTime = if (item.intervalStartTime > 0) timeFormat.format(Date(item.intervalStartTime)) else "--:--"
            val endTime = if (item.intervalEndTime > 0) timeFormat.format(Date(item.intervalEndTime)) else "--:--"
            holder.tvTimeRange.text = "$startTime - $endTime"
            holder.tvResetTime.text = "${formatRemainTime(item.intervalRemainsTime)}后重置"

            if (item.isUnlimited || item.totalCount == 0L) {
                holder.progressBar.progress = 0
                holder.tvPercent.text = "无限"
                holder.tvCurrentUsage.text = "无限额"
            } else {
                holder.progressBar.progress = item.usagePercent
                holder.tvPercent.text = "${item.usagePercent}%"
                holder.tvCurrentUsage.text = "${df.format(item.usageCount)} / ${df.format(item.totalCount)}"
            }
        } else {
            // 本周
            val weeklyStartTime = if (item.weeklyStartTime > 0) timeFormat.format(Date(item.weeklyStartTime)) else "--:--"
            val weeklyEndTime = if (item.weeklyEndTime > 0) timeFormat.format(Date(item.weeklyEndTime)) else "--:--"
            holder.tvTimeRange.text = "$weeklyStartTime - $weeklyEndTime"
            holder.tvResetTime.text = "${formatRemainTime(item.weeklyRemainsTime)}后重置"

            if (item.weeklyTotalCount == 0L) {
                holder.progressBar.progress = 0
                holder.tvPercent.text = "无限"
                holder.tvCurrentUsage.text = "无限额"
            } else {
                val weeklyPercent = ((item.weeklyUsageCount.toDouble() / item.weeklyTotalCount) * 100).toInt()
                holder.progressBar.progress = weeklyPercent
                holder.tvPercent.text = "${weeklyPercent}%"
                holder.tvCurrentUsage.text = "${df.format(item.weeklyUsageCount)} / ${df.format(item.weeklyTotalCount)}"
            }
        }
    }

    private fun formatRemainTime(millis: Long): String {
        if (millis <= 0) return "--"
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
    }

    override fun getItemCount() = items.size
}