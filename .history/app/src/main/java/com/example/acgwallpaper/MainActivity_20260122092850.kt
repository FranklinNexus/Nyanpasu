package com.example.acgwallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
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
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 🎨 V10.0 优雅交互版
 * 新特性：
 * - 频率选择器（对话框）
 * - 颜文字状态系统
 * - 优雅的加载状态
 * - Logo 左上角布局
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val activeTags = mutableListOf<String>()

    companion object {
        // 频率选项
        private val SCHEDULE_OPTIONS = arrayOf(
            "Daily 7:00 AM",
            "Every 6 Hours",
            "Every 12 Hours",
            "Every 24 Hours"
        )
        // 对应的值 (如果是 Daily 则是特殊处理，其他的对应小时数)
        private val SCHEDULE_VALUES = intArrayOf(-1, 6, 12, 24)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)

        // 1. 恢复设置
        loadPreview()
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        updateKaomoji(binding.seekBarStyle.progress)

        // 恢复频率显示
        val savedScheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
        binding.tvScheduleInfo.text = "${SCHEDULE_OPTIONS[savedScheduleIndex]} ▾"

        // 恢复标签
        val savedTags = prefs.getStringSet("SAVED_TAGS", emptySet()) ?: emptySet()
        savedTags.forEach { addChipToGroup(it) }

        // 2. 频率选择逻辑
        binding.tvScheduleInfo.setOnClickListener {
            showScheduleDialog()
        }

        // 3. 滑动条逻辑 (颜文字)
        binding.seekBarStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateKaomoji(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("STYLE", seekBar?.progress ?: 50).apply()
            }
        })

        // 4. 标签输入逻辑
        binding.etTagInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val text = binding.etTagInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    addChipToGroup(text)
                    saveTagsToPrefs()
                    binding.etTagInput.text?.clear()
                }
                return@setOnEditorActionListener true
            }
            false
        }

        // 5. 开关逻辑
        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DAILY_ENABLED", isChecked).apply()
            if (isChecked) {
                setupPeriodicWork()
                Toast.makeText(this, "Auto-Refresh ON ✅", Toast.LENGTH_SHORT).show()
            } else {
                cancelPeriodicWork()
            }
        }

        // 6. 刷新按钮 (带加载状态)
        binding.btnUpdate.setOnClickListener {
            startOneTimeWork(binding.seekBarStyle.progress, activeTags.toTypedArray())
        }
    }

    /**
     * 📋 显示频率选择对话框
     */
    private fun showScheduleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Refresh Frequency")
            .setItems(SCHEDULE_OPTIONS) { _, which ->
                // 保存选择
                val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
                prefs.edit().putInt("SCHEDULE_INDEX", which).apply()

                // 更新 UI
                binding.tvScheduleInfo.text = "${SCHEDULE_OPTIONS[which]} ▾"

                // 如果开关开着，立即应用新计划
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
        // 颜文字暗示系统
        val emoji = when (progress) {
            in 0..20 -> "( ˶˘ ³˘)🍬"         // 萌/亲亲
            in 21..40 -> "(｡•́‿•̀｡)✨"       // 可爱
            in 80..100 -> "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)💋" // 害羞/刺激
            in 60..79 -> "(¬‿¬)🍷"          // 懂的都懂
            else -> "(・_・)🎲"              // 发呆/随机
        }
        binding.tvStyleDesc.text = emoji
    }

    /**
     * 🚀 启动一次性任务（带优雅加载状态）
     */
    private fun startOneTimeWork(style: Int, tags: Array<String>) {
        // UI 进入加载状态
        binding.progressBar.visibility = View.VISIBLE
        binding.btnUpdate.isEnabled = false
        binding.btnUpdate.text = "Summoning... ⌛" // 状态反馈

        val inputData = workDataOf(
            "STYLE_VALUE" to style,
            "TAGS" to tags
        )

        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(this).enqueue(request)

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
            .observe(this) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    // UI 恢复
                    binding.progressBar.visibility = View.GONE
                    binding.btnUpdate.isEnabled = true
                    binding.btnUpdate.text = "Refresh ✨"

                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        loadPreview()
                    } else {
                        Toast.makeText(this, "Network Error (T_T)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    /**
     * ⏰ 设置周期性任务
     */
    private fun setupPeriodicWork() {
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        val style = prefs.getInt("STYLE", 50)
        val tags = activeTags.toTypedArray()
        val scheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)

        val inputData = workDataOf("STYLE_VALUE" to style, "TAGS" to tags)
        val workManager = WorkManager.getInstance(this)

        val requestBuilder: PeriodicWorkRequest.Builder

        if (scheduleIndex == 0) {
            // Daily 7:00 AM 逻辑
            // 计算距离下一个 7:00 AM 的时间
            requestBuilder = PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelayFor7AM(), TimeUnit.MILLISECONDS)
        } else {
            // 间隔逻辑 (6, 12, 24)
            val intervalHours = SCHEDULE_VALUES[scheduleIndex].toLong()
            requestBuilder = PeriodicWorkRequestBuilder<WallpaperWorker>(intervalHours, TimeUnit.HOURS)
        }

        val request = requestBuilder
            .setInputData(inputData)
            .addTag("AUTO_WALLPAPER")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "AUTO_JOB",
            ExistingPeriodicWorkPolicy.UPDATE, // 使用 UPDATE，这样修改频率后会立即生效
            request
        )
    }

    /**
     * 📅 计算到下一个 7:00 AM 的延迟时间
     */
    private fun calculateInitialDelayFor7AM(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        // 设置为今天 7:00 AM
        calendar.set(Calendar.HOUR_OF_DAY, 7)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // 如果已经过了今天的 7:00 AM，就设置为明天
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
     * 🏷️ 添加标签气泡到界面
     */
    private fun addChipToGroup(tagText: String) {
        if (activeTags.contains(tagText)) return
        val chip = Chip(this)
        chip.text = tagText
        chip.isCloseIconVisible = true
        chip.setChipBackgroundColorResource(android.R.color.white)
        chip.chipStrokeWidth = 1f
        chip.setChipStrokeColorResource(R.color.soft_pink)
        chip.setOnCloseIconClickListener {
            binding.chipGroupTags.removeView(chip)
            activeTags.remove(tagText)
            saveTagsToPrefs()
        }
        binding.chipGroupTags.addView(chip)
        activeTags.add(tagText)
    }

    /**
     * 💾 保存标签到 SharedPreferences
     */
    private fun saveTagsToPrefs() {
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("SAVED_TAGS", activeTags.toSet()).apply()
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
