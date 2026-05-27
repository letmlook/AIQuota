package com.aiquota.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aiquota.app.R
import com.aiquota.app.api.VolcEngineApi
import com.aiquota.app.databinding.FragmentVolcengineBinding
import com.aiquota.app.model.QuotaInfo
import com.aiquota.app.model.VolcPlanUsage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VolcEngineFragment : Fragment() {

    private var _binding: FragmentVolcengineBinding? = null
    private val binding get() = _binding!!

    private val volcApi = VolcEngineApi()
    private var autoRefreshJob: Job? = null
    private val autoRefreshIntervalMs = 5 * 60 * 1000L

    companion object {
        private const val REQUEST_LOGIN = 1001
        private const val PREFS_NAME = "ai_quota_prefs"
        private const val KEY_VOLC_DIGEST = "volc_digest"
        private const val KEY_VOLC_CSRF = "volc_csrf"
        private const val KEY_VOLC_ACCOUNT_ID = "volc_account_id"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVolcengineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSwipeRefresh()
        setupLoginButton()
        setupAutoRefresh()

        val (digest, csrf, _) = loadCredentials()
        if (digest.isNotEmpty() && csrf.isNotEmpty()) {
            queryQuota()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.volcengine_color)
        binding.swipeRefresh.setOnRefreshListener {
            val (digest, csrf, _) = loadCredentials()
            if (digest.isEmpty() || csrf.isEmpty()) {
                binding.swipeRefresh.isRefreshing = false
                showToast("请先登录火山引擎账号")
                return@setOnRefreshListener
            }
            queryQuota()
        }
    }

    private fun setupLoginButton() {
        binding.btnLoginVolc.setOnClickListener {
            val intent = Intent(requireContext(), VolcEngineLoginActivity::class.java)
            startActivityForResult(intent, REQUEST_LOGIN)
        }
    }

    private fun setupAutoRefresh() {
        binding.switchAutoRefreshVolc.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val (digest, csrf, _) = loadCredentials()
                if (digest.isEmpty() || csrf.isEmpty()) {
                    binding.switchAutoRefreshVolc.isChecked = false
                    showToast("请先登录火山引擎账号")
                    return@setOnCheckedChangeListener
                }
                startAutoRefresh()
            } else {
                stopAutoRefresh()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LOGIN && resultCode == android.app.Activity.RESULT_OK && data != null) {
            val digest = data.getStringExtra(VolcEngineLoginActivity.EXTRA_DIGEST) ?: ""
            val csrf = data.getStringExtra(VolcEngineLoginActivity.EXTRA_CSRF_TOKEN) ?: ""
            val accountId = data.getStringExtra(VolcEngineLoginActivity.EXTRA_ACCOUNT_ID) ?: ""

            if (digest.isNotEmpty() && csrf.isNotEmpty()) {
                saveCredentials(digest, csrf, accountId)
                showToast("登录成功")
                queryQuota()
            }
        }
    }

    private fun queryQuota() {
        hideAllCards()
        binding.swipeRefresh.isRefreshing = true

        val (digest, csrf, accountId) = loadCredentials()
        if (digest.isEmpty() || csrf.isEmpty()) {
            binding.swipeRefresh.isRefreshing = false
            showError(Exception("请先登录火山引擎账号"))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = volcApi.queryQuota(digest, csrf, accountId)
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
        hideAllCards()
        binding.cardVolcResult.visibility = View.VISIBLE

        // 套餐信息
        val subInfo = quota.volcSubscribeInfo
        binding.tvPlanName.text = quota.planName
        if (subInfo != null) {
            val start = volcApi.formatEndTime(subInfo.startTime)
            val end = volcApi.formatEndTime(subInfo.endTime)
            binding.tvPlanPeriod.text = "$start ~ $end"
            binding.tvPlanPeriod.visibility = View.VISIBLE
        } else {
            binding.tvPlanPeriod.text = "未找到订阅信息"
            binding.tvPlanPeriod.visibility = View.VISIBLE
        }

        // 状态
        binding.tvStatusVolc.text = quota.status
        binding.tvStatusVolc.setTextColor(
            getColor(if (quota.isAvailable) R.color.success else R.color.error)
        )

        // 三级进度条
        val container = binding.layoutUsageBars
        container.removeAllViews()

        for (usage in quota.volcPlanUsages) {
            container.addView(createUsageRow(usage))
        }

        // 原始数据
        binding.tvRawData.text = volcApi.lastRawResponse
        binding.tvRawData.visibility = View.GONE
    }

    private fun createUsageRow(usage: VolcPlanUsage): View {
        val context = requireContext()

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 24)
        }

        // 第一行：标签 + 百分比
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val label = TextView(context).apply {
            text = usage.levelLabel
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val percentText = TextView(context).apply {
            text = String.format("%.1f%%", usage.percent)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(
                when {
                    usage.percent >= 90 -> getColor(R.color.error)
                    usage.percent >= 70 -> getColor(R.color.warning)
                    else -> getColor(R.color.volcengine_color)
                }
            )
        }

        topRow.addView(label)
        topRow.addView(percentText)

        // 第二行：进度条
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = usage.percent.toInt()
            progressDrawable = context.getDrawable(R.drawable.progress_volcengine)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                12
            ).apply { topMargin = 8 }
        }

        // 第三行：刷新时间
        val resetText = TextView(context).apply {
            text = usage.resetTimeFormatted
            textSize = 12f
            setTextColor(getColor(R.color.text_hint))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        }

        wrapper.addView(topRow)
        wrapper.addView(progressBar)
        wrapper.addView(resetText)

        return wrapper
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(autoRefreshIntervalMs)
                queryQuota()
            }
        }
        showToast("每5分钟自动刷新")
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun hideAllCards() {
        binding.cardVolcResult.visibility = View.GONE
        binding.cardVolcError.visibility = View.GONE
    }

    private fun showError(error: Throwable) {
        hideAllCards()
        binding.cardVolcError.visibility = View.VISIBLE
        binding.tvVolcError.text = error.message ?: "未知错误"
    }

    private fun saveCredentials(digest: String, csrf: String, accountId: String) {
        requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VOLC_DIGEST, digest)
            .putString(KEY_VOLC_CSRF, csrf)
            .putString(KEY_VOLC_ACCOUNT_ID, accountId)
            .apply()
    }

    private fun loadCredentials(): Triple<String, String, String> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(KEY_VOLC_DIGEST, "") ?: "",
            prefs.getString(KEY_VOLC_CSRF, "") ?: "",
            prefs.getString(KEY_VOLC_ACCOUNT_ID, "") ?: ""
        )
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun getColor(colorResId: Int): Int {
        return requireContext().getColor(colorResId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoRefresh()
        _binding = null
    }
}
