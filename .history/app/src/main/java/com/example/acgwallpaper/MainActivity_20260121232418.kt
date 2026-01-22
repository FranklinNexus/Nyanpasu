package com.example.acgwallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import com.example.acgwallpaper.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 🌸 V5.0 MainActivity - 老婆生成器
 * 定制化壁纸：萌/欲滑动条 + 关键词召唤 + 每日托管
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 加载上次的壁纸预览
        loadPreview()

        // 2. 加载保存的偏好设置
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.etKeyword.setText(prefs.getString("KEYWORD", ""))
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        updateStyleText(binding.seekBarStyle.progress)

        // 3. 滑动条监听
        binding.seekBarStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateStyleText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 保存偏好
                prefs.edit().putInt("STYLE", seekBar?.progress ?: 50).apply()
            }
        })

        // 4. 每日推送开关监听
        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DAILY_ENABLED", isChecked).apply()
            if (isChecked) {
                setupDailyWork()
                Toast.makeText(this, "Daily Updates ON! ⏰", Toast.LENGTH_SHORT).show()
            } else {
                cancelDailyWork()
                Toast.makeText(this, "Daily Updates OFF 🔕", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. "立即更新" 按钮
        binding.btnUpdate.setOnClickListener {
            // 保存当前输入的关键词
            val keyword = binding.etKeyword.text.toString()
            prefs.edit().putString("KEYWORD", keyword).apply()

            // 开始任务
            startOneTimeWork(binding.seekBarStyle.progress, keyword)
        }
    }

    /**
     * 🎨 更新风格描述文本
     */
    private fun updateStyleText(progress: Int) {
        val desc = when (progress) {
            in 0..30 -> "Preference: Super Cute / Uniform / Maid 🍬"
            in 71..100 -> "Preference: Mature / Swimsuit / Sexy 💋"
            else -> "Preference: Balanced Random ✨"
        }
        binding.tvStyleDesc.text = desc
    }

    /**
     * 🚀 启动一次性任务
     */
    private fun startOneTimeWork(style: Int, keyword: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "Summoning..."
        binding.btnUpdate.isEnabled = false

        val inputData = workDataOf(
            "STYLE_VALUE" to style,
            "KEYWORD" to keyword
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
                        binding.tvStatus.text = "Success!"
                        // 2秒后隐藏状态文字
                        binding.tvStatus.postDelayed({
                            binding.tvStatus.visibility = View.GONE
                        }, 2000)
                    } else {
                        binding.tvStatus.text = "Failed (Check Net)"
                        binding.tvStatus.postDelayed({
                            binding.tvStatus.visibility = View.GONE
                        }, 3000)
                    }
                }
            }
    }

    /**
     * ⏰ 设置每日定时任务 (周期性任务)
     */
    private fun setupDailyWork() {
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        val style = prefs.getInt("STYLE", 50)
        val keyword = prefs.getString("KEYWORD", "") ?: ""

        val inputData = workDataOf(
            "STYLE_VALUE" to style,
            "KEYWORD" to keyword
        )

        val dailyRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
            .setInputData(inputData)
            .setInitialDelay(1, TimeUnit.HOURS) // 系统会自动调度到合适的时间
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("DAILY_WALLPAPER")
            .build()

        // 使用 UPDATE 策略：如果任务已存在，就用新设置替换
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
