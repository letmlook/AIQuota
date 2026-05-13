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

    private val df = DecimalFormat("#,###")
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModelName: TextView = view.findViewById(R.id.tvModelName)
        val tvIntervalTime: TextView = view.findViewById(R.id.tvIntervalTime)
        val tvIntervalReset: TextView = view.findViewById(R.id.tvIntervalReset)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val tvIntervalUsage: TextView = view.findViewById(R.id.tvIntervalUsage)
        val tvWeeklyTime: TextView = view.findViewById(R.id.tvWeeklyTime)
        val tvWeeklyReset: TextView = view.findViewById(R.id.tvWeeklyReset)
        val progressBarWeekly: ProgressBar = view.findViewById(R.id.progressBarWeekly)
        val tvWeeklyUsage: TextView = view.findViewById(R.id.tvWeeklyUsage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvModelName.text = item.modelName

        // 本次周期（5小时）
        val startTime = if (item.intervalStartTime > 0) timeFormat.format(Date(item.intervalStartTime)) else "--:--"
        val endTime = if (item.intervalEndTime > 0) timeFormat.format(Date(item.intervalEndTime)) else "--:--"
        holder.tvIntervalTime.text = "$startTime - $endTime"

        val resetStr = formatRemainTime(item.intervalRemainsTime)
        holder.tvIntervalReset.text = "${resetStr}后重置"

        if (item.isUnlimited || item.totalCount == 0L) {
            holder.progressBar.progress = 0
            holder.tvIntervalUsage.text = "无限额"
        } else {
            holder.progressBar.progress = item.usagePercent
            holder.tvIntervalUsage.text = "${df.format(item.usageCount)} / ${df.format(item.totalCount)}  ${item.usagePercent}% 已使用"
        }

        // 本周
        val weeklyStartTime = if (item.weeklyStartTime > 0) timeFormat.format(Date(item.weeklyStartTime)) else "--:--"
        val weeklyEndTime = if (item.weeklyEndTime > 0) timeFormat.format(Date(item.weeklyEndTime)) else "--:--"
        holder.tvWeeklyTime.text = "$weeklyStartTime - $weeklyEndTime"

        val weeklyResetStr = formatRemainTime(item.weeklyRemainsTime)
        holder.tvWeeklyReset.text = "${weeklyResetStr}后重置"

        if (item.weeklyTotalCount == 0L) {
            holder.progressBarWeekly.progress = 0
            holder.tvWeeklyUsage.text = "无限额"
        } else {
            val weeklyPercent = if (item.weeklyTotalCount > 0) {
                ((item.weeklyUsageCount.toDouble() / item.weeklyTotalCount) * 100).toInt()
            } else 0
            holder.progressBarWeekly.progress = weeklyPercent
            holder.tvWeeklyUsage.text = "${df.format(item.weeklyUsageCount)} / ${df.format(item.weeklyTotalCount)}  ${weeklyPercent}% 已使用"
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
