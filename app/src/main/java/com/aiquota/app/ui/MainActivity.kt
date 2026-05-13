package com.aiquota.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.aiquota.app.R
import com.aiquota.app.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var isUserSwiping = false // 防止滑动时触发导航回调循环

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 2 // 保持所有页面缓存
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                isUserSwiping = state == ViewPager2.SCROLL_STATE_DRAGGING
            }
            override fun onPageSelected(position: Int) {
                updateBottomNavigation(position)
                updateDebugInfo(position)
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_minimax -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_deepseek -> {
                    binding.viewPager.currentItem = 1
                    true
                }
                R.id.nav_settings -> {
                    binding.viewPager.currentItem = 2
                    true
                }
                else -> false
            }
        }
    }

    private fun updateBottomNavigation(position: Int) {
        val itemId = when (position) {
            0 -> R.id.nav_minimax
            1 -> R.id.nav_deepseek
            2 -> R.id.nav_settings
            else -> return
        }
        if (binding.bottomNavigation.selectedItemId != itemId) {
            isUserSwiping = true // 防止触发底部导航的回调
            binding.bottomNavigation.selectedItemId = itemId
        }
    }

    private fun updateDebugInfo(position: Int) {
        val provider = when (position) {
            0 -> "MiniMax"
            1 -> "DeepSeek"
            2 -> "设置"
            else -> ""
        }
        binding.tvDebugInfo.text = "调试模式 | $provider | ${dateFormat.format(Date())}"
    }
}
