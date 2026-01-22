package com.example.acgwallpaper

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import com.example.acgwallpaper.databinding.ActivityMainBinding
import java.io.File

/**
 * 🌸 V3.0 MainActivity - 弹药箱机制
 * 瞬间切换：使用预加载的本地图片，0 延迟
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 加载当前壁纸预览
        loadPreview("current_wallpaper.png")

        // 检查弹药箱，如果空的，自动开始填弹
        if (!File(filesDir, "next_wallpaper.png").exists()) {
            startWorker("PRELOAD_API") // 预加载一张
        }

        // 设置按钮监听器
        setupButtons()
    }

    /**
     * 🎮 设置按钮监听器
     */
    private fun setupButtons() {
        binding.btnRandom.setOnClickListener {
            performInstantSwitch("API")
        }

        binding.btnLatest.setOnClickListener {
            // TG 必须联网，无法预判，所以只能现下
            performInstantSwitch("TG")
        }

        binding.btnPrev.setOnClickListener {
            Toast.makeText(this, "History feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ⭐ 核心逻辑：瞬间切换 ⭐
     */
    private fun performInstantSwitch(type: String) {
        val nextFile = File(filesDir, "next_wallpaper.png")

        if (nextFile.exists() && type == "API") {
            // 1. 【有弹药】直接使用本地文件 (0延迟，无VPN)
            binding.tvStatus.text = "Instant Magic! ✨"

            try {
                val bitmap = BitmapFactory.decodeFile(nextFile.absolutePath)
                val wm = WallpaperManager.getInstance(this)
                val setHome = binding.cbHomeScreen.isChecked
                val setLock = binding.cbLockScreen.isChecked

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (setHome) wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    if (setLock) wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                } else {
                    wm.setBitmap(bitmap)
                }

                // 2. 更新 UI
                binding.ivPreview.setImageBitmap(bitmap)
                binding.ivPreview.animate().alpha(1f).duration = 300

                // 3. 将 next 移正为 current
                val currentFile = File(filesDir, "current_wallpaper.png")
                nextFile.renameTo(currentFile)

                // 4. 【重要】发射一颗后，立刻后台补弹
                startWorker("PRELOAD_API")

                binding.tvStatus.text = "Done! (Reloading...)"

            } catch (e: Exception) {
                e.printStackTrace()
                // 如果出错，转为联网下载
                startWorker("APPLY_API")
            }
        } else {
            // 2. 【无弹药】或者强制TG模式，走联网下载
            binding.tvStatus.text = if (type == "API") {
                "Catching Cuteness... 🎀"
            } else {
                "Loading Dreams... ☁️"
            }
            startWorker("APPLY_$type")
        }
    }

    /**
     * 🚀 启动后台任务
     */
    private fun startWorker(mode: String) {
        binding.progressBar.visibility = View.VISIBLE

        val inputData = workDataOf(
            "MODE" to mode,
            "SET_HOME" to binding.cbHomeScreen.isChecked,
            "SET_LOCK" to binding.cbLockScreen.isChecked
        )

        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(this).enqueue(request)

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
            .observe(this) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    binding.progressBar.visibility = View.GONE
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        if (mode.contains("APPLY")) {
                            loadPreview("current_wallpaper.png")
                            binding.tvStatus.text = "Success! (ﾉ>ω<)ﾉ"
                        } else {
                            // 只是预加载完成，静默处理
                            // binding.tvStatus.text = "Ammo Ready! 🔫"
                        }
                    } else {
                        binding.tvStatus.text = "Network Error (T_T)"
                    }
                }
            }
    }

    /**
     * 🖼️ 加载预览图
     */
    private fun loadPreview(filename: String) {
        val file = File(filesDir, filename)
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            binding.ivPreview.setImageBitmap(bitmap)
            binding.ivPreview.alpha = 0f
            binding.ivPreview.animate().alpha(1f).duration = 500
        }
    }
}