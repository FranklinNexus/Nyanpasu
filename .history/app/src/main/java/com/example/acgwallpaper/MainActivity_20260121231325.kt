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
     * 🔐 请求通知权限（Android 13+）
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * 🎮 设置按钮监听器
     */
    private fun setupButtons() {
        // 随机按钮（引擎 A - 无需 VPN）
        binding.btnRandom.setOnClickListener {
            startWallpaperTask("RANDOM")
        }

        // TG 按钮（引擎 B - 需 VPN）
        binding.btnLatest.setOnClickListener {
            startWallpaperTask("LATEST")
        }

        // 回退按钮（历史功能 - 预留）
        binding.btnPrev.setOnClickListener {
            if (historyStack.isNotEmpty()) {
                // V2.0 暂时只做提示，完整功能需要存储多张图片
                Toast.makeText(this, "History feature coming soon! ✨", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No history yet (｡•́︿•̀｡)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 🚀 启动壁纸更新任务
     */
    private fun startWallpaperTask(mode: String) {
        // 更新状态文本
        binding.tvStatus.text = if (mode == "RANDOM") {
            "Catching Cuteness... 🎀"
        } else {
            "Connecting TG... ✈️"
        }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRandom.isEnabled = false
        binding.btnLatest.isEnabled = false

        // 收集用户选项
        val setHome = binding.cbHomeScreen.isChecked
        val setLock = binding.cbLockScreen.isChecked

        // 检查至少选择了一个
        if (!setHome && !setLock) {
            Toast.makeText(this, "Please select at least one option! (｡•́︿•̀｡)", Toast.LENGTH_SHORT).show()
            resetUI()
            return
        }

        // 准备输入数据
        val inputData = workDataOf(
            "MODE" to mode,
            "SET_HOME" to setHome,
            "SET_LOCK" to setLock
        )

        // 创建工作请求
        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueue(request)

        // 观察任务状态
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
            .observe(this) { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            binding.tvStatus.text = "Success! (ﾉ>ω<)ﾉ"
                            resetUI()
                            
                            // 任务成功后，重新从本地加载图片显示
                            loadPreviewFromLocal()
                            
                            // 记录历史 URL（如果有返回的话）
                            val url = workInfo.outputData.getString("IMAGE_PATH")
                            if (url != null) {
                                historyStack.push(url)
                            }
                        }
                        
                        WorkInfo.State.FAILED -> {
                            binding.tvStatus.text = "Failed... Check Net/VPN (T_T)"
                            resetUI()
                        }
                        
                        else -> {
                            // Running, Enqueued, Blocked
                        }
                    }
                }
            }
    }

    /**
     * 🔄 重置 UI 状态
     */
    private fun resetUI() {
        binding.progressBar.visibility = View.GONE
        binding.btnRandom.isEnabled = true
        binding.btnLatest.isEnabled = true
    }

    /**
     * 🖼️ 从本地加载预览图
     */
    private fun loadPreviewFromLocal() {
        val file = File(filesDir, "current_wallpaper.png")
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                binding.ivPreview.setImageBitmap(bitmap)
                
                // 简单的淡入动画
                binding.ivPreview.alpha = 0f
                binding.ivPreview.animate().alpha(1f).duration = 500
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}