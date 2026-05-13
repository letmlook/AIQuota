package com.aiquota.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiquota.app.R
import com.aiquota.app.api.DeepSeekApi
import com.aiquota.app.api.MiniMaxApi
import com.aiquota.app.data.HistoryManager
import com.aiquota.app.databinding.ActivityMainBinding
import com.aiquota.app.databinding.DialogHistoryBinding
import com.aiquota.app.model.QuotaHistory
import com.aiquota.app.model.QuotaInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val miniMaxApi = MiniMaxApi()
    private val deepSeekApi = DeepSeekApi()
    private lateinit var historyManager: HistoryManager

    private var miniMaxAutoRefreshJob: Job? = null
    private var deepSeekAutoRefreshJob: Job? = null

    // Auto-refresh interval: 5 minutes
    private val autoRefreshIntervalMs = 5 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyManager = HistoryManager(this)
        setupViews()
        loadSavedKeys()
    }

    private fun setupViews() {
        // MiniMax 查询
        binding.btnQueryMinimax.setOnClickListener {
            val apiKey = binding.etMinimaxKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 MiniMax API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            queryMiniMaxQuota(apiKey)
        }

        // DeepSeek 查询
        binding.btnQueryDeepSeek.setOnClickListener {
            val apiKey = binding.etDeepSeekKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 DeepSeek API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            queryDeepSeekQuota(apiKey)
        }

        // 保存 Key
        binding.btnSaveKeys.setOnClickListener {
            saveApiKeys()
        }

        // MiniMax 自动刷新开关
        binding.switchAutoRefreshMinimax.setOnCheckedChangeListener { _, isChecked ->
            val apiKey = binding.etMinimaxKey.text.toString().trim()
            if (isChecked && apiKey.isEmpty()) {
                binding.switchAutoRefreshMinimax.isChecked = false
                Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                startAutoRefreshMiniMax(apiKey)
            } else {
                stopAutoRefreshMiniMax()
            }
        }

        // DeepSeek 自动刷新开关
        binding.switchAutoRefreshDeepSeek.setOnCheckedChangeListener { _, isChecked ->
            val apiKey = binding.etDeepSeekKey.text.toString().trim()
            if (isChecked && apiKey.isEmpty()) {
                binding.switchAutoRefreshDeepSeek.isChecked = false
                Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                startAutoRefreshDeepSeek(apiKey)
            } else {
                stopAutoRefreshDeepSeek()
            }
        }

        // MiniMax 历史
        binding.btnHistoryMinimax.setOnClickListener {
            showHistoryDialog("MiniMax")
        }

        // DeepSeek 历史
        binding.btnHistoryDeepSeek.setOnClickListener {
            showHistoryDialog("DeepSeek")
        }
    }

    private fun queryMiniMaxQuota(apiKey: String) {
        setLoading(true, isMiniMax = true)
        lifecycleScope.launch {
            try {
                val result = miniMaxApi.queryQuota(apiKey)
                result.onSuccess { quota ->
                    updateMiniMaxUI(quota)
                    historyManager.addRecord(
                        provider = "MiniMax",
                        planName = quota.planName,
                        status = quota.status,
                        isAvailable = quota.isAvailable,
                        remaining = quota.remainingFormatted,
                        remainingRaw = quota.remaining,
                        usagePercent = quota.usagePercent
                    )
                }.onFailure { error ->
                    binding.tvMinimaxResult.text = "查询失败: ${error.message}"
                    binding.cardMinimaxResult.visibility = View.VISIBLE
                    binding.progressBarMinimax.visibility = View.GONE
                    binding.tvUsagePercentMinimax.visibility = View.GONE
                }
            } finally {
                setLoading(false, isMiniMax = true)
            }
        }
    }

    private fun queryDeepSeekQuota(apiKey: String) {
        setLoading(true, isMiniMax = false)
        lifecycleScope.launch {
            try {
                val result = deepSeekApi.queryQuota(apiKey)
                result.onSuccess { quota ->
                    updateDeepSeekUI(quota)
                    historyManager.addRecord(
                        provider = "DeepSeek",
                        planName = quota.planName,
                        status = quota.status,
                        isAvailable = quota.isAvailable,
                        remaining = quota.remainingFormatted,
                        remainingRaw = quota.remaining,
                        usagePercent = quota.usagePercent
                    )
                }.onFailure { error ->
                    binding.tvDeepSeekResult.text = "查询失败: ${error.message}"
                    binding.cardDeepSeekResult.visibility = View.VISIBLE
                    binding.progressBarDeepSeek.visibility = View.GONE
                    binding.tvUsagePercentDeepSeek.visibility = View.GONE
                }
            } finally {
                setLoading(false, isMiniMax = false)
            }
        }
    }

    private fun updateMiniMaxUI(quota: QuotaInfo) {
        binding.apply {
            cardMinimaxResult.visibility = View.VISIBLE

            // 进度条
            progressBarMinimax.visibility = View.VISIBLE
            progressBarMinimax.max = 100
            progressBarMinimax.progress = quota.usagePercent

            // 百分比文字
            tvUsagePercentMinimax.visibility = View.VISIBLE
            tvUsagePercentMinimax.text = "${quota.usagePercent}%"

            // 状态颜色
            when {
                !quota.isAvailable -> {
                    cardMinimaxResult.strokeColor = getColor(R.color.error)
                    tvStatusMinimax.text = "⚠️ 额度用尽"
                    tvStatusMinimax.setTextColor(getColor(R.color.error))
                }
                quota.remaining < 1_000_000 -> { // 少于 1M tokens
                    cardMinimaxResult.strokeColor = getColor(R.color.warning)
                    tvStatusMinimax.text = "⚠️ 额度不足"
                    tvStatusMinimax.setTextColor(getColor(R.color.warning))
                }
                else -> {
                    cardMinimaxResult.strokeColor = getColor(R.color.success)
                    tvStatusMinimax.text = "✅ 正常"
                    tvStatusMinimax.setTextColor(getColor(R.color.success))
                }
            }

            tvStatusMinimax.visibility = View.VISIBLE

            // 详情文字
            val detailText = buildString {
                appendLine("套餐: ${quota.planName}")
                appendLine("已使用: ${quota.usedFormatted}")
                appendLine("总额度: ${quota.totalFormatted}")
                appendLine("剩余: ${quota.remainingFormatted}")
                if (quota.resetDate.isNotEmpty()) {
                    appendLine("重置日期: ${quota.resetDate}")
                }
            }
            tvMinimaxResult.text = detailText
            tvMinimaxResult.visibility = View.VISIBLE
        }
    }

    private fun updateDeepSeekUI(quota: QuotaInfo) {
        binding.apply {
            cardDeepSeekResult.visibility = View.VISIBLE

            // DeepSeek 没有固定额度，显示余额即可
            progressBarDeepSeek.visibility = View.GONE
            tvUsagePercentDeepSeek.visibility = View.GONE

            // 状态
            when {
                !quota.isAvailable -> {
                    cardDeepSeekResult.strokeColor = getColor(R.color.error)
                    tvStatusDeepSeek.text = "⚠️ 余额不足"
                    tvStatusDeepSeek.setTextColor(getColor(R.color.error))
                }
                quota.remaining < 1_000_000 -> { // 少于 1 元
                    cardDeepSeekResult.strokeColor = getColor(R.color.warning)
                    tvStatusDeepSeek.text = "⚠️ 余额不足"
                    tvStatusDeepSeek.setTextColor(getColor(R.color.warning))
                }
                else -> {
                    cardDeepSeekResult.strokeColor = getColor(R.color.success)
                    tvStatusDeepSeek.text = "✅ 正常"
                    tvStatusDeepSeek.setTextColor(getColor(R.color.success))
                }
            }

            tvStatusDeepSeek.visibility = View.VISIBLE

            // 详情
            val detailText = buildString {
                appendLine("余额: ${quota.remainingFormatted}")
                appendLine("赠金: ¥${String.format("%.2f", quota.grantedBalance)}")
                appendLine("充值: ¥${String.format("%.2f", quota.toppedUpBalance)}")
            }
            tvDeepSeekResult.text = detailText
            tvDeepSeekResult.visibility = View.VISIBLE
        }
    }

    private fun setLoading(loading: Boolean, isMiniMax: Boolean) {
        if (isMiniMax) {
            binding.btnQueryMinimax.isEnabled = !loading
            binding.progressMinimax.visibility = if (loading) View.VISIBLE else View.GONE
        } else {
            binding.btnQueryDeepSeek.isEnabled = !loading
            binding.progressDeepSeek.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun startAutoRefreshMiniMax(apiKey: String) {
        miniMaxAutoRefreshJob?.cancel()
        miniMaxAutoRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(autoRefreshIntervalMs)
                queryMiniMaxQuota(apiKey)
            }
        }
        Toast.makeText(this, "MiniMax 每5分钟自动刷新", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoRefreshMiniMax() {
        miniMaxAutoRefreshJob?.cancel()
        miniMaxAutoRefreshJob = null
    }

    private fun startAutoRefreshDeepSeek(apiKey: String) {
        deepSeekAutoRefreshJob?.cancel()
        deepSeekAutoRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(autoRefreshIntervalMs)
                queryDeepSeekQuota(apiKey)
            }
        }
        Toast.makeText(this, "DeepSeek 每5分钟自动刷新", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoRefreshDeepSeek() {
        deepSeekAutoRefreshJob?.cancel()
        deepSeekAutoRefreshJob = null
    }

    private fun showHistoryDialog(provider: String) {
        val history = historyManager.getHistoryByProvider(provider)
        if (history.isEmpty()) {
            Toast.makeText(this, "暂无查询记录", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogHistoryBinding.inflate(layoutInflater)
        val adapter = HistoryAdapter(history)
        dialogBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        dialogBinding.recyclerView.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("$provider 查询历史")
            .setView(dialogBinding.root)
            .setPositiveButton("清除记录") { _, _ ->
                historyManager.clearHistory()
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun saveApiKeys() {
        val miniMaxKey = binding.etMinimaxKey.text.toString().trim()
        val deepSeekKey = binding.etDeepSeekKey.text.toString().trim()

        getSharedPreferences("ai_quota_prefs", MODE_PRIVATE).edit().apply {
            putString("minimax_key", miniMaxKey)
            putString("deepseek_key", deepSeekKey)
            apply()
        }

        Toast.makeText(this, "API Key 已保存", Toast.LENGTH_SHORT).show()
    }

    private fun loadSavedKeys() {
        val prefs = getSharedPreferences("ai_quota_prefs", MODE_PRIVATE)
        binding.etMinimaxKey.setText(prefs.getString("minimax_key", "") ?: "")
        binding.etDeepSeekKey.setText(prefs.getString("deepseek_key", "") ?: "")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefreshMiniMax()
        stopAutoRefreshDeepSeek()
    }
}

// ============== History Adapter ==============

class HistoryAdapter(private val history: List<QuotaHistory>) :
    androidx.recyclerview.widget.RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val tvTime: android.widget.TextView = view.findViewById(R.id.tvTime)
        val tvRemaining: android.widget.TextView = view.findViewById(R.id.tvRemaining)
        val tvStatus: android.widget.TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
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
