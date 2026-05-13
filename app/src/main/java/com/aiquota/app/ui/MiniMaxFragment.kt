package com.aiquota.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiquota.app.R
import com.aiquota.app.api.MiniMaxApi
import com.aiquota.app.data.HistoryManager
import com.aiquota.app.databinding.FragmentMinimaxBinding
import com.aiquota.app.databinding.DialogHistoryBinding
import com.aiquota.app.model.QuotaInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiniMaxFragment : Fragment() {

    private var _binding: FragmentMinimaxBinding? = null
    private val binding get() = _binding!!

    private val miniMaxApi = MiniMaxApi()
    private lateinit var historyManager: HistoryManager
    private var autoRefreshJob: Job? = null
    private val autoRefreshIntervalMs = 5 * 60 * 1000L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMinimaxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = HistoryManager(requireContext())
        setupViews()
    }

    private fun setupViews() {
        binding.btnQueryMinimax.setOnClickListener {
            val token = getSavedToken()
            if (token.isEmpty()) {
                Toast.makeText(context, "请先在设置页配置 Token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            queryQuota(token)
        }

        binding.switchAutoRefreshMinimax.setOnCheckedChangeListener { _, isChecked ->
            val token = getSavedToken()
            if (isChecked && token.isEmpty()) {
                binding.switchAutoRefreshMinimax.isChecked = false
                Toast.makeText(context, "请先在设置页配置 Token", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                startAutoRefresh(token)
            } else {
                stopAutoRefresh()
            }
        }

        binding.btnHistoryMinimax.setOnClickListener {
            showHistory()
        }
    }

    private fun getSavedToken(): String {
        return requireContext().getSharedPreferences("ai_quota_prefs", android.content.Context.MODE_PRIVATE)
            .getString("minimax_token", "") ?: ""
    }

    override fun onResume() {
        super.onResume()
        val token = getSavedToken()
        if (token.isNotEmpty()) {
            queryQuota(token)
        }
    }

    private fun queryQuota(token: String) {
        setLoading(true)
        hideAllCards()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = miniMaxApi.queryQuota(token)
                result.onSuccess { quota ->
                    updateUI(quota)
                }.onFailure { error ->
                    showError(error)
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateUI(quota: QuotaInfo) {
        binding.cardMinimaxResult.visibility = View.VISIBLE

        // 状态颜色（根据使用百分比判断）
        val usagePercent = quota.usagePercent
        val (statusText, statusColor) = when {
            !quota.isAvailable -> "⚠️ 额度用尽" to R.color.error
            usagePercent >= 90 -> "⚠️ 不足" to R.color.warning
            else -> "✅ 正常" to R.color.success
        }
        binding.tvStatusMinimax.text = statusText
        binding.tvStatusMinimax.setTextColor(requireContext().getColor(statusColor))

        // 模型列表（显示所有模型）
        if (quota.minimaxModelRemains.isNotEmpty()) {
            binding.rvModelList.layoutManager = LinearLayoutManager(context)
            binding.rvModelList.adapter = ModelUsageAdapter(quota.minimaxModelRemains)
            binding.rvModelList.visibility = View.VISIBLE
        } else {
            binding.rvModelList.visibility = View.GONE
        }

        // 记录历史
        historyManager.addRecord(
            provider = "MiniMax",
            planName = quota.planName,
            status = quota.status,
            isAvailable = quota.isAvailable,
            remaining = quota.remainingFormatted,
            remainingRaw = quota.remaining,
            usagePercent = quota.usagePercent
        )
    }

    private fun showError(error: Throwable) {
        binding.cardMinimaxError.visibility = View.VISIBLE
        binding.tvMinimaxError.text = "❌ ${error.message}"
    }

    private fun hideAllCards() {
        binding.cardMinimaxResult.visibility = View.GONE
        binding.cardMinimaxError.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        binding.btnQueryMinimax.isEnabled = !loading
        binding.progressMinimax.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun startAutoRefresh(token: String) {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(autoRefreshIntervalMs)
                queryQuota(token)
            }
        }
        Toast.makeText(context, "每5分钟自动刷新", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun showHistory() {
        val history = historyManager.getHistoryByProvider("MiniMax")
        if (history.isEmpty()) {
            Toast.makeText(context, "暂无查询记录", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogHistoryBinding.inflate(layoutInflater)
        val adapter = HistoryAdapter(history)
        dialogBinding.recyclerView.layoutManager = LinearLayoutManager(context)
        dialogBinding.recyclerView.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setTitle("MiniMax 查询历史")
            .setView(dialogBinding.root)
            .setPositiveButton("清除记录") { _, _ ->
                historyManager.clearHistory()
                Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoRefreshJob?.cancel()
        _binding = null
    }
}
