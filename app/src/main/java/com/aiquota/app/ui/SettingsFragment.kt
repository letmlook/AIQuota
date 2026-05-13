package com.aiquota.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aiquota.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSavedTokens()
        setupViews()
    }

    private fun loadSavedTokens() {
        val prefs = requireContext().getSharedPreferences("ai_quota_prefs", android.content.Context.MODE_PRIVATE)
        binding.etMinimaxToken.setText(prefs.getString("minimax_token", "") ?: "")
        binding.etDeepSeekToken.setText(prefs.getString("deepseek_token", "") ?: "")
    }

    private fun setupViews() {
        binding.btnSave.setOnClickListener {
            saveTokens()
        }
    }

    private fun saveTokens() {
        val miniMaxToken = binding.etMinimaxToken.text.toString().trim()
        val deepSeekToken = binding.etDeepSeekToken.text.toString().trim()

        requireContext().getSharedPreferences("ai_quota_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("minimax_token", miniMaxToken)
            .putString("deepseek_token", deepSeekToken)
            .apply()

        Toast.makeText(context, "✅ Token 已保存", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
