package com.example.acgwallpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.example.acgwallpaper.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tagsMap = mutableMapOf<String, Boolean>()
    private val historyStack = java.util.ArrayDeque<String>()
    
    private val scheduleOptions = arrayOf("Daily 7:00 AM", "Every 6 Hours", "Every 12 Hours", "Every 24 Hours")
    
    private var isHomeEnabled = true
    private var isLockEnabled = false
    
    private var lastClickTime = 0L
    private val CLICK_INTERVAL = 1500L

    // --- 🥚 彩蛋 & 看板娘变量 ---
    private var logoClickCount = 0
    private val logoResetHandler = Handler(Looper.getMainLooper())
    private val speechHideHandler = Handler(Looper.getMainLooper())
    
    // 🎭 语料库 (不用联网，秒回，体验最好)
    private val mascotQuotes = listOf(
        "Ohiyo! Master~ ☀️",
        "Working hard today? 🍵",
        "Don't forget to drink water! 💧",
        "Need a new waifu? Let me find one! 🔍",
        "I'm charging... ⚡",
        "Pat pat~ (｡•̀ᴗ-)✧",
        "Is it time for anime yet? 📺",
        "Error 404: Sleep not found 💤",
        "Coding is magic! ✨",
        "Do you like white hair? Or black? 🤔",
        "System stable! All green! 💚",
        "Meow? 🐱",
        "Strict mode is powerful! 🔥",
        "Refresh for a surprise! 🎁",
        "Senpai noticed me! (๑>◡<๑)",
        "Today's mood: comfy~ ☁️",
        "Let's summon some beauties! 💝",
        "Ready to serve! (•̀ᴗ•́)و"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        loadPreview()
        
        // 恢复 UI 状态
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        updateKaomoji(binding.seekBarStyle.progress)
        
        val savedScheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
        binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[savedScheduleIndex]} ▾"

        val savedTagsSet = prefs.getStringSet("SAVED_TAGS_V2", emptySet()) ?: emptySet()
        savedTagsSet.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) {
                addChipToGroup(parts[0], parts[1].toBoolean())
            } else {
                addChipToGroup(entry, false)
            }
        }

        // --- 🎀 Logo 点击事件 (看板娘交互核心) ---
        binding.ivLogo.setOnClickListener {
            bounceAnimate(it)
            handleLogoClick()
        }

        // 调度选择器
        binding.tvScheduleInfo.setOnClickListener { showScheduleDialog() }

        // 滑动条监听
        binding.seekBarStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateKaomoji(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("STYLE", seekBar?.progress ?: 50).apply()
            }
        })

        // 标签输入框监听
        binding.etTagInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val text = binding.etTagInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    addChipToGroup(text, false)
                    saveTagsToPrefs()
                    binding.etTagInput.text?.clear()
                }
                return@setOnEditorActionListener true
            }
            false
        }

        // 自动刷新开关
        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DAILY_ENABLED", isChecked).apply()
            if (isChecked) {
                setupPeriodicWork()
                Toast.makeText(this, "Auto-Refresh ON ✅", Toast.LENGTH_SHORT).show()
            } else {
                cancelPeriodicWork()
            }
        }

        // 刷新按钮
        binding.btnUpdate.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < CLICK_INTERVAL) {
                Toast.makeText(this, "Cooling down... ☕", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastClickTime = currentTime
            bounceAnimate(it)
            backupCurrentToHistory()
            startOneTimeWork()
        }
        
        updateToggleButtons()
        
        // Home/Lock 按钮
        binding.btnToggleHome.setOnClickListener { 
            bounceAnimate(it)
            isHomeEnabled = !isHomeEnabled
            updateToggleButtons()
            if (isHomeEnabled) applyCurrentToTarget(WallpaperManager.FLAG_SYSTEM)
        }
        
        binding.btnToggleLock.setOnClickListener { 
            bounceAnimate(it)
            isLockEnabled = !isLockEnabled
            updateToggleButtons()
            if (isLockEnabled) applyCurrentToTarget(WallpaperManager.FLAG_LOCK)
        }
        
        // 撤销按钮
        binding.btnUndo.setOnClickListener {
            bounceAnimate(it)
            if (historyStack.isNotEmpty()) {
                val lastFile = historyStack.pop()
                restoreWallpaper(lastFile)
                Toast.makeText(this, "Restored! 🔙", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 保存按钮
        binding.btnSave.setOnClickListener {
            bounceAnimate(it)
            saveCurrentToGallery()
        }
    }
    
    // --- 🥚 彩蛋处理逻辑 ---
    private fun handleLogoClick() {
        // 1. 增加计数
        logoClickCount++
        
        // 2. 如果连续点击 10 次 -> 触发彩蛋 🎉
        if (logoClickCount >= 10) {
            showDeveloperDialog()
            logoClickCount = 0 // 重置
            return
        }

        // 3. 重置计时器 (如果2秒内没继续点，重置计数)
        logoResetHandler.removeCallbacksAndMessages(null)
        logoResetHandler.postDelayed({
            logoClickCount = 0
        }, 2000)

        // 4. 显示随机台词 ✨
        showRandomQuote()
    }

    /**
     * 🗨️ 显示看板娘随机台词
     */
    private fun showRandomQuote() {
        val quote = mascotQuotes.random()
        binding.tvMascotSpeech.text = quote
        
        // 淡入动画
        binding.tvMascotSpeech.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        // 3秒后自动淡出
        speechHideHandler.removeCallbacksAndMessages(null)
        speechHideHandler.postDelayed({
            binding.tvMascotSpeech.animate()
                .alpha(0f)
                .setDuration(500)
                .start()
        }, 3000)
    }

    /**
     * 🎁 开发者彩蛋弹窗
     */
    private fun showDeveloperDialog() {
        // 🔧 这里填入你的个人信息
        val blogUrl = "https://github.com/YourUsername" 
        val name = "Your Name"
        
        AlertDialog.Builder(this)
            .setTitle("👨‍💻 Developer & Credits")
            .setMessage(
                "Hey! Thanks for using ACG Daily.\n\n" +
                "I'm $name, a cross-disciplinary builder bridging AI and hardware.\n\n" +
                "Enjoying the app? Visit my blog or buy me a coffee! ☕"
            )
            .setPositiveButton("Visit Blog") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(blogUrl))
                startActivity(intent)
            }
            .setNeutralButton("Close", null)
            .setIcon(R.mipmap.ic_launcher) // 显示你的 App 图标
            .show()
    }

    // --- 📍 Chip 逻辑 (去数值化：🔒 和 🎲) ---
    private fun addChipToGroup(tagText: String, isStrict: Boolean) {
        if (tagsMap.containsKey(tagText)) return
        tagsMap[tagText] = isStrict
        
        val chip = Chip(this)
        chip.text = tagText
        chip.isCheckable = true
        chip.isChecked = isStrict
        chip.isCloseIconVisible = true
        updateChipStyle(chip, isStrict)
        
        chip.setOnClickListener {
            bounceAnimate(it)
            val newState = !tagsMap[tagText]!!
            tagsMap[tagText] = newState
            chip.isChecked = newState
            updateChipStyle(chip, newState)
            saveTagsToPrefs()
            
            // 👇 去数值化：改用 🔒 (锁定) 和 🎲 (概率)
            val msg = if(newState) "Strict Mode 🔒" else "Soft Mode 🎲"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        chip.setOnCloseIconClickListener {
            binding.chipGroupTags.removeView(chip)
            tagsMap.remove(tagText)
            saveTagsToPrefs()
        }
        
        binding.chipGroupTags.addView(chip)
    }

    /**
     * 🎨 Q弹动画
     */
    private fun bounceAnimate(view: View) {
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
    }
    
    /**
     * 🎨 视觉修正：深粉底+粗白勾高对比度方案
     */
    private fun updateToggleButtons() {
        // --- Home 按钮 ---
        if (isHomeEnabled) {
            binding.btnToggleHome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF80AB"))
            binding.btnToggleHome.setTextColor(Color.WHITE)
            binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnToggleHome.setIconResource(R.drawable.ic_check_bold)
        } else {
            binding.btnToggleHome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            binding.btnToggleHome.setTextColor(Color.GRAY)
            binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.GRAY)
            binding.btnToggleHome.setIconResource(android.R.drawable.checkbox_off_background)
        }
        
        // --- Lock 按钮 ---
        if (isLockEnabled) {
            binding.btnToggleLock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF80AB"))
            binding.btnToggleLock.setTextColor(Color.WHITE)
            binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnToggleLock.setIconResource(R.drawable.ic_check_bold)
        } else {
            binding.btnToggleLock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            binding.btnToggleLock.setTextColor(Color.GRAY)
            binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.GRAY)
            binding.btnToggleLock.setIconResource(android.R.drawable.checkbox_off_background)
        }
    }
    
    /**
     * 🎨 Chip 样式更新
     */
    private fun updateChipStyle(chip: Chip, isStrict: Boolean) {
        if (isStrict) {
            // 硬标签：粉色底 + 白字 (🔒 锁定)
            chip.chipBackgroundColor = ColorStateList.valueOf(getColor(R.color.soft_pink))
            chip.setTextColor(Color.WHITE)
            chip.chipStrokeWidth = 0f
            chip.closeIconTint = ColorStateList.valueOf(Color.WHITE)
        } else {
            // 软标签：灰色底 + 灰字 (🎲 概率)
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
            chip.setTextColor(Color.GRAY)
            chip.chipStrokeColor = ColorStateList.valueOf(Color.LTGRAY)
            chip.chipStrokeWidth = 2f
            chip.closeIconTint = ColorStateList.valueOf(Color.GRAY)
        }
    }
    
    /**
     * 🗂️ 历史备份
     */
    private fun backupCurrentToHistory() {
        val currentFile = File(filesDir, "current_wallpaper.png")
        if (currentFile.exists()) {
            val timestamp = System.currentTimeMillis()
            val backupFile = File(filesDir, "history_$timestamp.png")
            currentFile.copyTo(backupFile, overwrite = true)
            historyStack.push(backupFile.name)
            if (historyStack.size > 5) {
                val old = historyStack.removeLast()
                File(filesDir, old).delete()
            }
        }
    }
    
    /**
     * 🔙 恢复历史壁纸
     */
    private fun restoreWallpaper(filename: String) {
        val historyFile = File(filesDir, filename)
        if (historyFile.exists()) {
            val currentFile = File(filesDir, "current_wallpaper.png")
            historyFile.copyTo(currentFile, overwrite = true)
            if (isHomeEnabled) applyCurrentToTarget(WallpaperManager.FLAG_SYSTEM)
            if (isLockEnabled) applyCurrentToTarget(WallpaperManager.FLAG_LOCK)
            loadPreview()
        }
    }
    
    /**
     * 💾 保存到相册
     */
    private fun saveCurrentToGallery() {
        val file = File(filesDir, "current_wallpaper.png")
        if (!file.exists()) return
        
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ACG_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ACGWallpaper")
        }
        
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it).use { out ->
                FileInputStream(file).copyTo(out!!)
            }
            Toast.makeText(this, "Saved to Gallery! 📸", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 🚀 立即刷新壁纸
     */
    private fun startOneTimeWork() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnUpdate.isEnabled = false
        binding.btnUpdate.text = "Summoning... ⌛"
        
        val strictTags = tagsMap.filter { it.value }.keys.toTypedArray()
        val softTags = tagsMap.filter { !it.value }.keys.toTypedArray()
        
        val inputData = workDataOf(
            "STYLE_VALUE" to binding.seekBarStyle.progress,
            "STRICT_TAGS" to strictTags,
            "SOFT_TAGS" to softTags,
            "SET_HOME" to isHomeEnabled,
            "SET_LOCK" to isLockEnabled
        )
        
        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInputData(inputData)
            .build()
        
        WorkManager.getInstance(this).enqueue(request)
        
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
            .observe(this) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUpdate.isEnabled = true
                    binding.btnUpdate.text = "Refresh ✨"
                    
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        loadPreview()
                    } else {
                        Toast.makeText(this, "Network congestion >_<", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }
    
    /**
     * 🖼️ 应用壁纸到指定目标
     */
    private fun applyCurrentToTarget(flag: Int) {
        val file = File(filesDir, "current_wallpaper.png")
        if (!file.exists()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val wm = WallpaperManager.getInstance(this@MainActivity)
                wm.setBitmap(bitmap, null, true, flag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 🕐 调度选择器弹窗
     */
    private fun showScheduleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Refresh Frequency")
            .setItems(scheduleOptions) { _, which ->
                getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("SCHEDULE_INDEX", which)
                    .apply()
                binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[which]} ▾"
                if (binding.switchDaily.isChecked) setupPeriodicWork()
            }
            .show()
    }
    
    /**
     * 😊 更新颜文字状态
     */
    private fun updateKaomoji(progress: Int) {
        val emoji = when (progress) {
            in 0..20 -> "( ˶˘ ³˘)🍬"
            in 21..40 -> "(｡•́‿•̀｡)✨"
            in 80..100 -> "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)💋"
            in 60..79 -> "(¬‿¬)🍷"
            else -> "(・_・)🎲"
        }
        binding.tvStyleDesc.text = emoji
    }
    
    /**
     * ⏰ 设置定期任务
     */
    private fun setupPeriodicWork() {
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        val style = prefs.getInt("STYLE", 50)
        val strictTags = tagsMap.filter { it.value }.keys.toTypedArray()
        val softTags = tagsMap.filter { !it.value }.keys.toTypedArray()
        val scheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
        val scheduleValues = intArrayOf(-1, 6, 12, 24)
        
        val inputData = workDataOf(
            "STYLE_VALUE" to style,
            "STRICT_TAGS" to strictTags,
            "SOFT_TAGS" to softTags,
            "SET_HOME" to isHomeEnabled,
            "SET_LOCK" to isLockEnabled
        )
        
        val requestBuilder = if (scheduleIndex == 0) {
            // Daily 7:00 AM
            PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
        } else {
            PeriodicWorkRequestBuilder<WallpaperWorker>(
                scheduleValues[scheduleIndex].toLong(),
                TimeUnit.HOURS
            )
        }
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AUTO_JOB",
            ExistingPeriodicWorkPolicy.UPDATE,
            requestBuilder
                .setInputData(inputData)
                .addTag("AUTO_WALLPAPER")
                .build()
        )
    }
    
    /**
     * 🔕 取消定期任务
     */
    private fun cancelPeriodicWork() {
        WorkManager.getInstance(this).cancelUniqueWork("AUTO_JOB")
    }
    
    /**
     * 💾 保存标签到偏好设置
     */
    private fun saveTagsToPrefs() {
        val set = tagsMap.map { "${it.key}|${it.value}" }.toSet()
        getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("SAVED_TAGS_V2", set)
            .apply()
    }
    
    /**
     * 🖼️ 加载预览图
     */
    private fun loadPreview() {
        val file = File(filesDir, "current_wallpaper.png")
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            binding.ivPreview.setImageBitmap(bitmap)
            binding.ivPreview.alpha = 0f
            binding.ivPreview.animate().alpha(1f).duration = 500
        }
    }
}
