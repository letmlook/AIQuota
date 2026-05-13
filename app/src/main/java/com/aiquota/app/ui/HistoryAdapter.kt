package com.aiquota.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiquota.app.R
import com.aiquota.app.model.QuotaHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(private val history: List<QuotaHistory>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvRemaining: TextView = view.findViewById(R.id.tvRemaining)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = history[position]
        holder.tvTime.text = dateFormat.format(Date(record.queryTime))
        holder.tvRemaining.text = record.remaining

        val context = holder.itemView.context
        if (record.isAvailable) {
            holder.tvStatus.text = "✅ 正常"
            holder.tvStatus.setTextColor(context.getColor(R.color.success))
        } else {
            holder.tvStatus.text = "⚠️ 不足"
            holder.tvStatus.setTextColor(context.getColor(R.color.warning))
        }
    }

    override fun getItemCount() = history.size
}
