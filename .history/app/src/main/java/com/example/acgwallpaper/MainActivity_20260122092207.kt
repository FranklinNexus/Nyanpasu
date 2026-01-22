package com.example.acgwallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import com.example.acgwallpaper.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 🌸 V6.0 MainActivity - 优雅标签系统
 * Pure/Soft ↔ Elegant/Bold + 动态 Chip 标签
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    // 用一个 List 来存当前的标签
    private val activeTags = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadPreview()

        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)

        // 1. 恢复设置
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        updateStyleText(binding.seekBarStyle.progress)

        // 2. 恢复标签 (关键)
        val savedTags = prefs.getStringSet("SAVED_TAGS", emptySet()) ?: emptySet()
        savedTags.forEach { addChipToGroup(it) }

        // 3. 滑动条逻辑
        binding.seekBarStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateStyleText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("STYLE", seekBar?.progress ?: 50).apply()
            }
        })

        // 4. 输入框逻辑：监听回车键 -> 生成 Chip
        binding.etTagInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

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

        // 5. 每日推送开关
        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DAILY_ENABLED", isChecked).apply()
            if (isChecked) {
                setupDailyWork()
                Toast.makeText(this, "Daily Inspiration ON ⏰", Toast.LENGTH_SHORT).show()
            } else {
                cancelDailyWork()
                Toast.makeText(this, "Daily Inspiration OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // 6. 更新按钮
        binding.btnUpdate.setOnClickListener {
            // 把 List 转成 Array 传给 Worker
            startOneTimeWork(binding.seekBarStyle.progress, activeTags.toTypedArray())
        }
    }

    /**
     * 🌟 核心方法：动态添加标签气泡
     */
    private fun addChipToGroup(tagText: String) {
        if (activeTags.contains(tagText)) return // 避免重复

        val chip = Chip(this)
        chip.text = tagText
        chip.isCloseIconVisible = true // 显示删除小叉叉
        chip.setChipBackgroundColorResource(android.R.color.white)
        chip.chipStrokeWidth = 2f // 细边框
        chip.setChipStrokeColorResource(R.color.soft_pink)
        chip.setTextColor(getColor(R.color.soft_pink))

        // 点击删除事件
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
     * 🎨 更新风格描述文本（V9.5 精准体型控制版）
     */
    private fun updateStyleText(progress: Int) {
        // 精细化滑动条反馈，明确提示强约束区
        val desc = when (progress) {
            in 0..20 -> "Target: 贫乳 / 萝莉 / 白丝 (强约束) 🍬"  // 极左强约束
            in 21..40 -> "Target: 萝莉 / 可爱 ✨"                // 偏左
            in 80..100 -> "Target: 巨乳 / 御姐 / 黑丝 (强约束) 💋" // 极右强约束
            in 60..79 -> "Target: 丰满 / 魅惑 💃"                // 偏右
            else -> "Target: 随机美少女 🎲"                       // 中间
        }
        binding.tvStyleDesc.text = desc
    }

    /**
     * 🚀 启动一次性任务
     */
    private fun startOneTimeWork(style: Int, tags: Array<String>) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnUpdate.isEnabled = false

        val inputData = workDataOf(
            "STYLE_VALUE" to style,
            "TAGS" to tags // 传数组
        )

        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueue(request)

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
            .observe(this) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUpdate.isEnabled = true

                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        loadPreview()
                        Toast.makeText(this, "✨ Updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Check Network", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    /**
     * ⏰ 设置每日定时任务
     */
    private fun setupDailyWork() {
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        val style = prefs.getInt("STYLE", 50)
        // 获取当前所有标签
        val tags = activeTags.toTypedArray()

        val inputData = workDataOf(
            "STYLE_VALUE" to style,
            "TAGS" to tags
        )

        val dailyRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
            .setInputData(inputData)
            .setInitialDelay(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("DAILY_WALLPAPER")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DAILY_JOB",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyRequest
        )
    }

    /**
     * ❌ 取消每日任务
     */
    private fun cancelDailyWork() {
        WorkManager.getInstance(this).cancelUniqueWork("DAILY_JOB")
    }

    /**
     * 🖼️ 加载预览图
     */
    private fun loadPreview() {
        val file = File(filesDir, "current_wallpaper.png")
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                binding.ivPreview.setImageBitmap(bitmap)
                binding.ivPreview.alpha = 0f
                binding.ivPreview.animate().alpha(1f).duration = 500
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
