package com.aiquota.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiquota.app.R
import com.aiquota.app.model.ModelUsage

class DeepSeekUsageAdapter(
    private val items: List<ModelUsage>
) : RecyclerView.Adapter<DeepSeekUsageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModelName: TextView = view.findViewById(R.id.tvModelName)
        val tvModelUsage: TextView = view.findViewById(R.id.tvModelUsage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deepseek_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvModelName.text = item.modelName
        holder.tvModelUsage.text = item.usageFormatted
    }

    override fun getItemCount() = items.size
}
