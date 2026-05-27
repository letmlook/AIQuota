package com.aiquota.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.aiquota.app.R
import com.aiquota.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isUserSwiping = false

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
        binding.viewPager.offscreenPageLimit = 3
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                isUserSwiping = state == ViewPager2.SCROLL_STATE_DRAGGING
            }
            override fun onPageSelected(position: Int) {
                updateBottomNavigation(position)
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
                R.id.nav_volcengine -> {
                    binding.viewPager.currentItem = 2
                    true
                }
                R.id.nav_settings -> {
                    binding.viewPager.currentItem = 3
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
            2 -> R.id.nav_volcengine
            3 -> R.id.nav_settings
            else -> return
        }
        if (binding.bottomNavigation.selectedItemId != itemId) {
            isUserSwiping = true
            binding.bottomNavigation.selectedItemId = itemId
        }
    }
}