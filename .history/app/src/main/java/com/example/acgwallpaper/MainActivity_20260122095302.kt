package com.example.acgwallpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
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
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 🎨 V11.5 即时响应版
 * 新特性：
 * - 默认软标签（灰色，20%）
 * - 开关即时生效（协程优化）
 * - 优化用户体验
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tagsMap = mutableMapOf<String, Boolean>()
    private val historyStack = java.util.ArrayDeque<String>()

    private val scheduleOptions = arrayOf("Daily 7:00 AM", "Every 6 Hours", "Every 12 Hours", "Every 24 Hours")

    // 默认只开 Home
    private var isHomeEnabled = true
    private var isLockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)

        loadPreview()

        // 恢复 UI
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        isHomeEnabled = prefs.getBoolean("HOME_ENABLED", true)
        isLockEnabled = prefs.getBoolean("LOCK_ENABLED", false)
        updateKaomoji(binding.seekBarStyle.progress)

        val savedScheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
        binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[savedScheduleIndex]} ▾"

        // 恢复标签
        val savedTagsSet = prefs.getStringSet("SAVED_TAGS_V2", emptySet()) ?: emptySet()
        savedTagsSet.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) {
                addChipToGroup(parts[0], parts[1].toBoolean())
            } else {
                addChipToGroup(entry, false)
            }
        }

        updateToggleButtons()

        // --- 事件监听 ---

        binding.tvScheduleInfo.setOnClickListener { showScheduleDialog() }

        binding.seekBarStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateKaomoji(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("STYLE", seekBar?.progress ?: 50).apply()
            }
        })

        // 标签输入：默认权重改为 false (20% 软标签)
        binding.etTagInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val text = binding.etTagInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    addChipToGroup(text, false) // 👈 默认 false (灰色软标签)
                    saveTagsToPrefs()
                    binding.etTagInput.text?.clear()
                }
                return@setOnEditorActionListener true
            }
            false
        }

        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DAILY_ENABLED", isChecked).apply()
            if (isChecked) {
                setupPeriodicWork()
                Toast.makeText(this, "Auto-Refresh ON ✅", Toast.LENGTH_SHORT).show()
            } else {
                cancelPeriodicWork()
            }
        }

        binding.btnUpdate.setOnClickListener {
            backupCurrentToHistory()
            startOneTimeWork()
        }

        // --- 核心改动：开关即时生效 ---

        binding.btnToggleHome.setOnClickListener {
            isHomeEnabled = !isHomeEnabled
            prefs.edit().putBoolean("HOME_ENABLED", isHomeEnabled).apply()
            updateToggleButtons()
            // 👇 如果开启，立即把当前图应用到桌面
            if (isHomeEnabled) applyCurrentToTarget(WallpaperManager.FLAG_SYSTEM)
        }

        binding.btnToggleLock.setOnClickListener {
            isLockEnabled = !isLockEnabled
            prefs.edit().putBoolean("LOCK_ENABLED", isLockEnabled).apply()
            updateToggleButtons()
            // 👇 如果开启，立即把当前图应用到锁屏
            if (isLockEnabled) applyCurrentToTarget(WallpaperManager.FLAG_LOCK)
        }

        binding.btnUndo.setOnClickListener {
            if (historyStack.isNotEmpty()) {
                val lastFile = historyStack.pop()
                restoreWallpaper(lastFile)
                Toast.makeText(this, "Restored! 🔙", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            saveCurrentToGallery()
        }
    }

    /**
     * ⚡ 新增：立即应用当前壁纸到指定目标
     * 使用协程在后台执行，防止卡顿 UI
     */
    private fun applyCurrentToTarget(flag: Int) {
        val file = File(filesDir, "current_wallpaper.png")
        if (!file.exists()) {
            Toast.makeText(this, "No wallpaper yet", Toast.LENGTH_SHORT).show()
            return
        }

        // 使用协程在后台设置，防止卡顿 UI
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val wm = WallpaperManager.getInstance(this@MainActivity)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // rect 传 null，allowBackup 传 true
                    wm.setBitmap(bitmap, null, true, flag)
                } else {
                    wm.setBitmap(bitmap)
                }

                withContext(Dispatchers.Main) {
                    val targetName = if (flag == WallpaperManager.FLAG_SYSTEM) "Home" else "Lock"
                    Toast.makeText(this@MainActivity, "$targetName Updated! ✨", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to apply", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 🏷️ 添加标签气泡
     */
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
            // 切换状态
            val currentState = tagsMap[tagText] ?: false
            val newState = !currentState

            tagsMap[tagText] = newState
            chip.isChecked = newState
            updateChipStyle(chip, newState)
            saveTagsToPrefs()

            val msg = if (newState) "🔒 Strict (100%)" else "🎲 Soft (~20%)"
            Toast.makeText(this, "$tagText: $msg", Toast.LENGTH_SHORT).show()
        }

        chip.setOnCloseIconClickListener {
            binding.chipGroupTags.removeView(chip)
            tagsMap.remove(tagText)
            saveTagsToPrefs()
        }

        binding.chipGroupTags.addView(chip)
    }

    /**
     * 🎨 更新 Chip 样式
     */
    private fun updateChipStyle(chip: Chip, isStrict: Boolean) {
        if (isStrict) {
            // 粉色：强约束 (100%)
            chip.chipBackgroundColor = ColorStateList.valueOf(getColor(R.color.soft_pink))
            chip.setTextColor(Color.WHITE)
            chip.chipStrokeWidth = 0f
            chip.closeIconTint = ColorStateList.valueOf(Color.WHITE)
        } else {
            // 灰色：软约束 (~20%)
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
            chip.setTextColor(Color.GRAY)
            chip.chipStrokeColor = ColorStateList.valueOf(Color.LTGRAY)
            chip.chipStrokeWidth = 2f
            chip.closeIconTint = ColorStateList.valueOf(Color.GRAY)
        }
    }

    /**
     * 🚀 启动一次性任务
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
                        Toast.makeText(this, "Net Error (Rate Limit?)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    /**
     * 📦 备份当前壁纸到历史
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

            // 恢复时也立即应用
            if (isHomeEnabled) applyCurrentToTarget(WallpaperManager.FLAG_SYSTEM)
            if (isLockEnabled) applyCurrentToTarget(WallpaperManager.FLAG_LOCK)

            loadPreview()
        }
    }

    /**
     * 📸 保存当前壁纸到相册
     */
    private fun saveCurrentToGallery() {
        val file = File(filesDir, "current_wallpaper.png")
        if (!file.exists()) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            } else {
                Toast.makeText(this, "Requires Android 10+", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🎚️ 更新 Home/Lock 按钮样式
     */
    private fun updateToggleButtons() {
        if (isHomeEnabled) {
            binding.btnToggleHome.backgroundTintList = ColorStateList.valueOf(getColor(R.color.soft_pink))
            binding.btnToggleHome.setTextColor(Color.WHITE)
            binding.btnToggleHome.setIconResource(android.R.drawable.checkbox_on_background)
            binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.WHITE)
        } else {
            binding.btnToggleHome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            binding.btnToggleHome.setTextColor(Color.GRAY)
            binding.btnToggleHome.setIconResource(android.R.drawable.checkbox_off_background)
            binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.GRAY)
        }

        if (isLockEnabled) {
            binding.btnToggleLock.backgroundTintList = ColorStateList.valueOf(getColor(R.color.soft_pink))
            binding.btnToggleLock.setTextColor(Color.WHITE)
            binding.btnToggleLock.setIconResource(android.R.drawable.checkbox_on_background)
            binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.WHITE)
        } else {
            binding.btnToggleLock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            binding.btnToggleLock.setTextColor(Color.GRAY)
            binding.btnToggleLock.setIconResource(android.R.drawable.checkbox_off_background)
            binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.GRAY)
        }
    }

    /**
     * 📋 显示频率选择对话框
     */
    private fun showScheduleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Refresh Frequency")
            .setItems(scheduleOptions) { _, which ->
                getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
                    .edit().putInt("SCHEDULE_INDEX", which).apply()
                binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[which]} ▾"
                if (binding.switchDaily.isChecked) {
                    setupPeriodicWork()
                    Toast.makeText(this, "Schedule Updated ⏰", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * 😊 更新颜文字状态指示器
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
     * ⏰ 设置周期性任务
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

        val requestBuilder: PeriodicWorkRequest.Builder = if (scheduleIndex == 0) {
            // Daily 7:00 AM 逻辑
            PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelayFor7AM(), TimeUnit.MILLISECONDS)
        } else {
            // 间隔逻辑
            PeriodicWorkRequestBuilder<WallpaperWorker>(scheduleValues[scheduleIndex].toLong(), TimeUnit.HOURS)
        }

        val request = requestBuilder
            .setInputData(inputData)
            .addTag("AUTO_WALLPAPER")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AUTO_JOB",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * 📅 计算到下一个 7:00 AM 的延迟时间
     */
    private fun calculateInitialDelayFor7AM(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 7)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return calendar.timeInMillis - now
    }

    /**
     * ❌ 取消周期性任务
     */
    private fun cancelPeriodicWork() {
        WorkManager.getInstance(this).cancelUniqueWork("AUTO_JOB")
    }

    /**
     * 💾 保存标签到 SharedPreferences
     */
    private fun saveTagsToPrefs() {
        val set = tagsMap.map { "${it.key}|${it.value}" }.toSet()
        getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
            .edit().putStringSet("SAVED_TAGS_V2", set).apply()
    }

    /**
     * 🖼️ 加载预览图片（带淡入动画）
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
