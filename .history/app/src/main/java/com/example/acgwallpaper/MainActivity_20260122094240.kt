package com.example.acgwallpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
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
import androidx.work.*
import com.example.acgwallpaper.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 🎨 V11.0 专业控制版
 * 新特性：
 * - 软/硬标签系统（20% vs 100%）
 * - 历史记录 & 回退功能
 * - 保存到相册
 * - Home/Lock 独立开关
 * - 标签滚动区域（限高 80dp）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 标签数据: Map<TagName, IsStrict>
    // true = 100% 硬标签 (粉色), false = 20% 软标签 (灰色)
    private val tagsMap = mutableMapOf<String, Boolean>()

    // 历史记录 (存文件名)
    private val historyStack = java.util.ArrayDeque<String>()

    private val scheduleOptions = arrayOf("Daily 7:00 AM", "Every 6 Hours", "Every 12 Hours", "Every 24 Hours")
    private val scheduleValues = intArrayOf(-1, 6, 12, 24)

    // 壁纸应用目标开关
    private var isHomeEnabled = true
    private var isLockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)

        loadPreview()

        // 恢复 UI 状态
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        isHomeEnabled = prefs.getBoolean("HOME_ENABLED", true)
        isLockEnabled = prefs.getBoolean("LOCK_ENABLED", false)
        updateKaomoji(binding.seekBarStyle.progress)

        val savedScheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
        binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[savedScheduleIndex]} ▾"

        // 恢复标签 (格式: "tag|true", "tag2|false")
        val savedTagsSet = prefs.getStringSet("SAVED_TAGS_V2", emptySet()) ?: emptySet()
        savedTagsSet.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) {
                addChipToGroup(parts[0], parts[1].toBoolean())
            } else {
                addChipToGroup(entry, false) // 兼容旧数据
            }
        }

        updateToggleButtons() // 初始化按钮样式

        // --- 事件监听 ---

        // 1. 频率选择
        binding.tvScheduleInfo.setOnClickListener { showScheduleDialog() }

        // 2. 滑动条
        binding.seekBarStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateKaomoji(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("STYLE", seekBar?.progress ?: 50).apply()
            }
        })

        // 3. 标签输入
        binding.etTagInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val text = binding.etTagInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    addChipToGroup(text, true) // 新加的默认是硬标签
                    saveTagsToPrefs()
                    binding.etTagInput.text?.clear()
                }
                return@setOnEditorActionListener true
            }
            false
        }

        // 4. 自动开关
        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DAILY_ENABLED", isChecked).apply()
            if (isChecked) {
                setupPeriodicWork()
                Toast.makeText(this, "Auto-Refresh ON ✅", Toast.LENGTH_SHORT).show()
            } else {
                cancelPeriodicWork()
            }
        }

        // 5. 刷新按钮
        binding.btnUpdate.setOnClickListener {
            // 在刷新前，把当前图片存入历史
            backupCurrentToHistory()
            startOneTimeWork()
        }

        // --- 新功能按钮 ---

        // 6. 桌面/锁屏开关
        binding.btnToggleHome.setOnClickListener {
            isHomeEnabled = !isHomeEnabled
            prefs.edit().putBoolean("HOME_ENABLED", isHomeEnabled).apply()
            updateToggleButtons()
        }
        binding.btnToggleLock.setOnClickListener {
            isLockEnabled = !isLockEnabled
            prefs.edit().putBoolean("LOCK_ENABLED", isLockEnabled).apply()
            updateToggleButtons()
        }

        // 7. 回退 (Undo)
        binding.btnUndo.setOnClickListener {
            if (historyStack.isNotEmpty()) {
                val lastFile = historyStack.pop()
                restoreWallpaper(lastFile)
                Toast.makeText(this, "Restored! 🔙", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show()
            }
        }

        // 8. 保存原图
        binding.btnSave.setOnClickListener {
            saveCurrentToGallery()
        }
    }

    /**
     * 🏷️ 添加标签气泡到界面
     * @param tagText 标签文本
     * @param isStrict true=硬标签(100%), false=软标签(20%)
     */
    private fun addChipToGroup(tagText: String, isStrict: Boolean) {
        if (tagsMap.containsKey(tagText)) return

        tagsMap[tagText] = isStrict

        val chip = Chip(this)
        chip.text = tagText
        chip.isCheckable = true
        chip.isChecked = isStrict
        chip.isCloseIconVisible = true

        // 样式设置
        updateChipStyle(chip, isStrict)

        // 点击切换 软/硬 状态
        chip.setOnClickListener {
            val newState = !tagsMap[tagText]!!
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
            // 硬标签：粉色背景，白字
            chip.chipBackgroundColor = ColorStateList.valueOf(getColor(R.color.soft_pink))
            chip.setTextColor(Color.WHITE)
            chip.chipStrokeWidth = 0f
            chip.closeIconTint = ColorStateList.valueOf(Color.WHITE)
        } else {
            // 软标签：白色背景，灰字，灰边框
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.WHITE)
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

        // 分离 软/硬 标签
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
                        Toast.makeText(this, "Net Error (T_T)", Toast.LENGTH_SHORT).show()
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

            // 限制历史记录数量为 5
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
            // 恢复为 current
            val currentFile = File(filesDir, "current_wallpaper.png")
            historyFile.copyTo(currentFile, overwrite = true)

            // 设置壁纸
            val bitmap = BitmapFactory.decodeFile(currentFile.absolutePath)
            val wm = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (isHomeEnabled) wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                if (isLockEnabled) wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            } else {
                wm.setBitmap(bitmap)
            }

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
                // Android 9 及以下，需要权限处理
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
        // 更新 Home 按钮样式
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

        // 更新 Lock 按钮样式
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
        val scheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)

        // 分离软硬标签
        val strictTags = tagsMap.filter { it.value }.keys.toTypedArray()
        val softTags = tagsMap.filter { !it.value }.keys.toTypedArray()

        val inputData = workDataOf(
            "STYLE_VALUE" to style,
            "STRICT_TAGS" to strictTags,
            "SOFT_TAGS" to softTags,
            "SET_HOME" to isHomeEnabled,
            "SET_LOCK" to isLockEnabled
        )

        val workManager = WorkManager.getInstance(this)

        val requestBuilder: PeriodicWorkRequest.Builder

        if (scheduleIndex == 0) {
            // Daily 7:00 AM 逻辑
            requestBuilder = PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelayFor7AM(), TimeUnit.MILLISECONDS)
        } else {
            // 间隔逻辑 (6, 12, 24)
            val intervalHours = scheduleValues[scheduleIndex].toLong()
            requestBuilder = PeriodicWorkRequestBuilder<WallpaperWorker>(intervalHours, TimeUnit.HOURS)
        }

        val request = requestBuilder
            .setInputData(inputData)
            .addTag("AUTO_WALLPAPER")
            .build()

        workManager.enqueueUniquePeriodicWork(
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
