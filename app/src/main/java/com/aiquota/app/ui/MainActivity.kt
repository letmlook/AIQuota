package com.aiquota.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiquota.app.api.DeepSeekApi
import com.aiquota.app.api.MiniMaxApi
import com.aiquota.app.databinding.ActivityMainBinding
import com.aiquota.app.model.QuotaInfo
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val miniMaxApi = MiniMaxApi()
    private val deepSeekApi = DeepSeekApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
    }

    private fun setupViews() {
        // MiniMax 查询按钮
        binding.btnQueryMinimax.setOnClickListener {
            val apiKey = binding.etMinimaxKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 MiniMax API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            queryMiniMaxQuota(apiKey)
        }

        // DeepSeek 查询按钮
        binding.btnQueryDeepSeek.setOnClickListener {
            val apiKey = binding.etDeepSeekKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 DeepSeek API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            queryDeepSeekQuota(apiKey)
        }

        // 保存 Key 按钮
        binding.btnSaveKeys.setOnClickListener {
            saveApiKeys()
        }

        // 加载已保存的 Key
        loadSavedKeys()
    }

    private fun queryMiniMaxQuota(apiKey: String) {
        setLoading(true, isMiniMax = true)
        lifecycleScope.launch {
            try {
                val result = miniMaxApi.queryQuota(apiKey)
                result.onSuccess { quota ->
                    binding.tvMinimaxResult.text = formatQuotaInfo(quota, "MiniMax")
                    binding.cardMinimaxResult.visibility = View.VISIBLE
                }.onFailure { error ->
                    binding.tvMinimaxResult.text = "查询失败: ${error.message}"
                    binding.cardMinimaxResult.visibility = View.VISIBLE
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
                    binding.tvDeepSeekResult.text = formatQuotaInfo(quota, "DeepSeek")
                    binding.cardDeepSeekResult.visibility = View.VISIBLE
                }.onFailure { error ->
                    binding.tvDeepSeekResult.text = "查询失败: ${error.message}"
                    binding.cardDeepSeekResult.visibility = View.VISIBLE
                }
            } finally {
                setLoading(false, isMiniMax = false)
            }
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

    private fun formatQuotaInfo(quota: QuotaInfo, provider: String): String {
        return buildString {
            appendLine("=== $provider 套餐信息 ===")
            appendLine()
            appendLine("套餐类型: ${quota.planName}")
            appendLine("状态: ${quota.status}")
            appendLine()
            appendLine("--- 用量 ---")
            appendLine("已使用: ${quota.usedFormatted}")
            appendLine("额度上限: ${quota.totalFormatted}")
            appendLine("剩余: ${quota.remainingFormatted}")
            appendLine()
            appendLine("使用比例: ${quota.usagePercent}%")
            if (quota.resetDate.isNotEmpty()) {
                appendLine("重置日期: ${quota.resetDate}")
            }
        }
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
        val miniMaxKey = prefs.getString("minimax_key", "") ?: ""
        val deepSeekKey = prefs.getString("deepseek_key", "") ?: ""
        
        binding.etMinimaxKey.setText(miniMaxKey)
        binding.etDeepSeekKey.setText(deepSeekKey)
    }
}
