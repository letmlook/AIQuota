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

class MiniMaxFragment : Fragment() {

    private var _binding: FragmentMinimaxBinding? = null
    private val binding get() = _binding!!

    private val miniMaxApi = MiniMaxApi()
    private lateinit var historyManager: HistoryManager
    private var autoRefreshJob: Job? = null
    private val autoRefreshIntervalMs = 5 * 60 * 1000L

    // 全局 Tab 状态: 0 = 本次周期, 1 = 本周
    private var currentTab = 0
    private var modelAdapter: ModelUsageAdapter? = null

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
        // 默认加载数据
        val token = getSavedToken()
        if (token.isNotEmpty()) {
            queryQuota(token)
        }
    }

    private fun setupViews() {
        // 下拉刷新
        binding.swipeRefresh.setColorSchemeResources(R.color.minimax_color)
        binding.swipeRefresh.setOnRefreshListener {
            val token = getSavedToken()
            if (token.isEmpty()) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(context, "请先在设置页配置 Token", Toast.LENGTH_SHORT).show()
                return@setOnRefreshListener
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

        // Tab 切换
        binding.tabInterval.setOnClickListener {
            switchTab(0)
        }

        binding.tabWeekly.setOnClickListener {
            switchTab(1)
        }
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        modelAdapter?.showInterval = tab
        modelAdapter?.notifyDataSetChanged()
        if (tab == 0) {
            binding.tabInterval.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabInterval.setTextColor(requireContext().getColor(R.color.text_primary))
            binding.tabWeekly.setBackgroundResource(android.R.color.transparent)
            binding.tabWeekly.setTextColor(requireContext().getColor(R.color.text_hint))
        } else {
            binding.tabWeekly.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabWeekly.setTextColor(requireContext().getColor(R.color.text_primary))
            binding.tabInterval.setBackgroundResource(android.R.color.transparent)
            binding.tabInterval.setTextColor(requireContext().getColor(R.color.text_hint))
        }
        modelAdapter?.notifyDataSetChanged()
    }

    private fun getSavedToken(): String {
        return requireContext().getSharedPreferences("ai_quota_prefs", android.content.Context.MODE_PRIVATE)
            .getString("minimax_token", "") ?: ""
    }

    private fun queryQuota(token: String) {
        hideAllCards()
        binding.swipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = miniMaxApi.queryQuota(token)
                result.onSuccess { quota ->
                    updateUI(quota)
                }.onFailure { error ->
                    showError(error)
                }
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateUI(quota: QuotaInfo) {
        binding.cardMinimaxResult.visibility = View.VISIBLE

        // 状态颜色
        val usagePercent = quota.usagePercent
        val (statusText, statusColor) = when {
            !quota.isAvailable -> "⚠️ 额度用尽" to R.color.error
            usagePercent >= 90 -> "⚠️ 不足" to R.color.warning
            else -> "✅ 正常" to R.color.success
        }
        binding.tvStatusMinimax.text = statusText
        binding.tvStatusMinimax.setTextColor(requireContext().getColor(statusColor))

        // 模型列表
        if (quota.minimaxModelRemains.isNotEmpty()) {
            binding.rvModelList.layoutManager = LinearLayoutManager(context)
            modelAdapter = ModelUsageAdapter(quota.minimaxModelRemains)
            modelAdapter?.showInterval = currentTab
            binding.rvModelList.adapter = modelAdapter
            binding.rvModelList.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        } else {
            binding.rvModelList.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
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