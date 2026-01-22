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
import android.widget.ImageView
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
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tagsMap = mutableMapOf<String, Boolean>()
    private val historyStack = java.util.ArrayDeque<String>()
    
    private val scheduleOptions = arrayOf("Daily 7:00 AM", "Every 6 Hours", "Every 12 Hours", "Every 24 Hours")
    
    // --- 🎨 三态系统 ---
    // 0 = Off (Gray)
    // 1 = Sync/Primary (Pink)
    // 2 = Independent (Blue)
    private var homeState = 1 // 默认 Home 开启（粉色同步）
    private var lockState = 0 // 默认 Lock 关闭
    
    // 当前预览的是哪一张 (Home or Lock)
    private var isPreviewingHome = true
    
    private var lastClickTime = 0L
    private val CLICK_INTERVAL = 1500L

    // --- 🥚 看板娘系统 ---
    private var logoClickCount = 0
    private val logoResetHandler = Handler(Looper.getMainLooper())
    private val speechHideHandler = Handler(Looper.getMainLooper())
    
    private val mascotQuotes = listOf(
        // 日常问候
        "Ohiyo! (｡･ω･｡)", "おかえりなさい！", "Yahallo~", "Konbanwa~",
        "Otsukare-sama desu!", "Selamat Pagi!", "Konnichiwa (´｡• ᵕ •｡`)",
        
        // 萌系口癖
        "Nyanpasu~", "Tuturu~ ♪", "Uguu~", "Auau...", "Nipah~ ☆", 
        "Gao~", "Hawawa~", "Hau~", "Pyon pyon~", "Moe moe kyun!",
        "Tehepero~", "Waku waku!", "Fuee~", "Pikya!", "Hae~?",
        "Nyaa~ (=^･ω･^=)", "Wan!", "Cheerio!", "Poi?", "Nano desu!",
        "Desu wa~", "Ara ara~", "Umu!", "Poyon~", "Nico nico ni~",
        
        // 经典梗
        "Fun things are fun!", "Rice is a side dish!", "Kininarimasu!",
        "Safe? Out? Safe!", "Timotei~ Timotei~", "I buy sausage!",
        "Secret Society BLANKET.", "Explosion!!", "Eris pads her chest.",
        "Kazuma desu.", "El Psy Kongroo.", "I am mad scientist!",
        "Daga otoko da.", "Just according to keikaku.",
        "The moon is beautiful, isn't it?", "I want to eat your pancreas.",
        "Menma, we found you!", "Dango dango dango~",
        "Zettai Ryouiki is justice!", "Flat is justice!",
        "Megane is the best!", "Twintails supremacy!",
        
        // 冷门深度梗
        "Pipiru piru piru pipiru pi~",
        "Ask not the sparrow how the eagle soars.",
        "Don't lose your way!", "Let's all love Lain.",
        "Present day, present time.", "Zetsubou shita!",
        "Humanity has declined.", "Watashi wa pan desu.",
        "Panzer vor!", "Ginko was here.", "Odd Taxi?",
        "Keep your hands off Eizouken!", "Ping Pong is life.",
        "Dekomori desu!", "Wicked Lord Shingan!", "Ai yo!",
        "Kira kira doki doki!", "Bucchake arienaiss!",
        "Omochikaeri~!", "Daijoubu, mondai nai.",
        
        // 游戏梗
        "Gacha time!", "Critical hit!", "Level up!", 
        "Save point reached.", "Boss music starts...",
        "Respawning...", "GG WP!", "Rush B!",
        "Praise the Sun!", "You died.", "Hey, you're finally awake.",
        "Doktah?", "Tabibito-san?", "Kanchou?",
        "Sensei?", "Producer-san?", "Shikikan?",
        
        // 程序员梗
        "No bugs today, please~", "Compiling happiness...",
        "Git push your dreams!", "404: Sadness not found.",
        "Sudo make me a sandwich.", "Hello World!",
        "Stack overflow? Stack hugs!", "while(alive) { code(); }",
        "Error 418: I'm a teapot.", "const happiness = true;",
        
        // 互动颜文字
        "Pat pat~ (｡•̀ᴗ-)✧", "Meow? (=^･ｪ･^=)", 
        "Loading cuteness... [▓▓▓▓▓▓] 100%",
        "*stares* (・_・)", "Boop! (•ω•)", "Zzz... (-.-)zzZ",
        "*nods* (๑•̀ㅂ•́)و✧", "Ehehe~ (⁄ ⁄•⁄ω⁄•⁄ ⁄)",
        "Chu~ (˘з˘)", "*spins* (ノ´ヮ`)ノ*: ･ﾟ",
        "Ganbare! ٩(•̀ᴗ•́)و", "Yosh! (ง •̀_•́)ง",
        "Yatta! ヽ(^o^)丿", "Honto ni? (｡･ω･｡)",
        "Maa maa~ (´ ω `)", "Dame da yo~ (>_<)",
        "(　･ω･)⊃", "( ´ ▽ ` )ﾉ", "(つ✧ω✧)つ",
        "ヾ(･ω･*)ﾉ", "(*´∀｀*)", "(｡◕ ∀ ◕｡)",
        "( ˘ω˘ )", "ε-(´∀｀*)", "(๑˃̵ᴗ˂̵)و",
        
        // 更多日常
        "Don't forget to drink water~", "Rice is ready!",
        "It's anime o'clock!", "Time for a break?",
        "Have you eaten?", "Stretch time~",
        "Compiling dreams...", "Debugging reality...",
        "System all green!", "Cache refreshed!",
        "Connection stable~", "Happiness.exe running.",
        
        // 打破第四面墙
        "Are you there?", "Staring contest? (*･ω･)",
        "Don't ignore me~ (´･ω･`)", "Battery check!",
        "Remember to rest!", "You matter!",
        "Sending good vibes~", "It's okay to be slow.",
        "One step at a time~", "Believe in yourself!",
        
        // 更多萌系
        "Nyoro~n", "Puwa puwa~", "Funya~", "Mukyuu~",
        "Hora hora~", "Mou~!", "Yare yare...", "Saa...",
        "Etto...", "Ano ne...", "Nee nee~", "Moshi moshi~",
        "Ja ne~", "Mata ne!", "Oyasumi~", "Itadakimasu!",
        "Gochisousama!", "Tadaima!", "Itterasshai!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        
        // 恢复三态状态
        homeState = prefs.getInt("HOME_STATE", 1)
        lockState = prefs.getInt("LOCK_STATE", 0)
        
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

        // --- 🚀 启动时自动说一句话 ---
        showRandomQuote()

        // --- Logo 点击交互 ---
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
        
        // --- 🎨 三态按钮逻辑 ---
        binding.btnToggleHome.setOnClickListener { 
            bounceAnimate(it)
            // 循环: 0 -> 1 -> 2 -> 0
            homeState = (homeState + 1) % 3
            prefs.edit().putInt("HOME_STATE", homeState).apply()
            updateToggleButtons()
            
            // 立即应用 (如果是 0，就不动)
            if (homeState > 0) applyCurrentToTarget(WallpaperManager.FLAG_SYSTEM, isHome = true)
        }
        
        binding.btnToggleLock.setOnClickListener { 
            bounceAnimate(it)
            lockState = (lockState + 1) % 3
            prefs.edit().putInt("LOCK_STATE", lockState).apply()
            updateToggleButtons()
            
            if (lockState > 0) applyCurrentToTarget(WallpaperManager.FLAG_LOCK, isHome = false)
        }
        
        // 撤销按钮
        binding.btnUndo.setOnClickListener {
            bounceAnimate(it)
            undoWallpaper()
        }
        
        // 保存按钮
        binding.btnSave.setOnClickListener {
            bounceAnimate(it)
            saveCurrentToGallery()
        }
        
        // --- 🎯 堆叠卡片点击切换 ---
        binding.stackContainer.setOnClickListener {
            val homeFile = File(filesDir, "wallpaper_home.png")
            val lockFile = File(filesDir, "wallpaper_lock.png")
            
            // 判断是否处于双色模式 (Pink + Blue 或 Blue + Pink)
            val isDualMode = (homeState != lockState) && (homeState > 0 && lockState > 0)
            
            if (isDualMode && homeFile.exists() && lockFile.exists()) {
                animateStackSwap() // ✨ 执行堆叠切换动画
            } else {
                bounceAnimate(binding.cardFront) // 单图模式只震动一下
            }
        }
    }
    
    // --- 🥚 彩蛋逻辑 ---
    private fun handleLogoClick() {
        logoClickCount++
        
        if (logoClickCount >= 10) {
            showDeveloperDialog()
            logoClickCount = 0
            return
        }

        logoResetHandler.removeCallbacksAndMessages(null)
        logoResetHandler.postDelayed({
            logoClickCount = 0
        }, 2000)

        // 每次点击换一句话
        showRandomQuote()
    }

    /**
     * 🗨️ 显示随机台词
     */
    private fun showRandomQuote() {
        val quote = mascotQuotes.random()
        binding.tvMascotSpeech.text = quote
        
        // 淡入动画
        binding.tvMascotSpeech.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        // 5秒后淡出
        speechHideHandler.removeCallbacksAndMessages(null)
        speechHideHandler.postDelayed({
            binding.tvMascotSpeech.animate()
                .alpha(0f)
                .setDuration(800)
                .start()
        }, 5000)
    }

    /**
     * 🎁 开发者彩蛋弹窗
     */
    private fun showDeveloperDialog() {
        val blogUrl = "https://github.com/YourUsername" // 👈 记得换成你的博客地址
        
        AlertDialog.Builder(this)
            .setTitle("👨‍💻 Developer")
            .setMessage(
                "Hi! I'm a builder exploring AI & Hardware.\n\n" +
                "Hope this app brightens your day! ✨\n\n" +
                "Check out my blog or buy me a coffee? ☕"
            )
            .setPositiveButton("Visit Blog") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(blogUrl))
                startActivity(intent)
            }
            .setNeutralButton("Close", null)
            .setIcon(R.mipmap.ic_launcher)
            .show()
    }

    /**
     * 🎨 三态按钮视觉更新
     */
    private fun updateToggleButtons() {
        val pink = ColorStateList.valueOf(Color.parseColor("#FF80AB"))
        val blue = ColorStateList.valueOf(Color.parseColor("#64B5F6"))
        val gray = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
        
        // Home Button
        when (homeState) {
            1 -> { // Pink (Sync)
                binding.btnToggleHome.backgroundTintList = pink
                binding.btnToggleHome.setTextColor(Color.WHITE)
                binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.WHITE)
                binding.btnToggleHome.setIconResource(R.drawable.ic_check_bold)
            }
            2 -> { // Blue (Independent)
                binding.btnToggleHome.backgroundTintList = blue
                binding.btnToggleHome.setTextColor(Color.WHITE)
                binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.WHITE)
                binding.btnToggleHome.setIconResource(R.drawable.ic_check_bold)
            }
            else -> { // Off
                binding.btnToggleHome.backgroundTintList = gray
                binding.btnToggleHome.setTextColor(Color.GRAY)
                binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.GRAY)
                binding.btnToggleHome.setIconResource(android.R.drawable.checkbox_off_background)
            }
        }
        
        // Lock Button
        when (lockState) {
            1 -> { // Pink
                binding.btnToggleLock.backgroundTintList = pink
                binding.btnToggleLock.setTextColor(Color.WHITE)
                binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.WHITE)
                binding.btnToggleLock.setIconResource(R.drawable.ic_check_bold)
            }
            2 -> { // Blue
                binding.btnToggleLock.backgroundTintList = blue
                binding.btnToggleLock.setTextColor(Color.WHITE)
                binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.WHITE)
                binding.btnToggleLock.setIconResource(R.drawable.ic_check_bold)
            }
            else -> {
                binding.btnToggleLock.backgroundTintList = gray
                binding.btnToggleLock.setTextColor(Color.GRAY)
                binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.GRAY)
                binding.btnToggleLock.setIconResource(android.R.drawable.checkbox_off_background)
            }
        }
        
        loadPreview() // 按钮状态改变可能影响指示器显示
    }
    
    /**
     * 🖼️ 预览加载逻辑 (叠排核心)
     */
    /**
     * 🎴 核心：堆叠预览加载逻辑
     */
    private fun loadPreview() {
        val homeFile = File(filesDir, "wallpaper_home.png")
        val lockFile = File(filesDir, "wallpaper_lock.png")
        
        // 判断是否需要显示堆叠效果 (双流模式)
        // 条件：两个开关都开，且状态不同 (一个Sync一个Independent)
        val isDualMode = (homeState != lockState) && (homeState > 0 && lockState > 0)
        
        if (isDualMode) {
            // === 堆叠模式 ===
            binding.cardBack.visibility = View.VISIBLE
            binding.tvViewIndicator.visibility = View.VISIBLE
            
            if (isPreviewingHome) {
                // 正面看 Home，背面藏 Lock
                loadImageToView(homeFile, binding.ivFront)
                loadImageToView(lockFile, binding.ivBack)
                
                binding.tvViewIndicator.text = "Editing: Home Screen 🏠"
                binding.tvViewIndicator.setTextColor(Color.parseColor("#FF80AB")) // Pink
            } else {
                // 正面看 Lock，背面藏 Home
                loadImageToView(lockFile, binding.ivFront)
                loadImageToView(homeFile, binding.ivBack)
                
                binding.tvViewIndicator.text = "Editing: Lock Screen 🔒"
                binding.tvViewIndicator.setTextColor(Color.parseColor("#64B5F6")) // Blue
            }
            
        } else {
            // === 单图模式 (同步 或 单开) ===
            binding.cardBack.visibility = View.GONE
            binding.tvViewIndicator.visibility = View.INVISIBLE // 隐藏指示器，保持极简
            
            // 决定显示哪张图
            val targetFile = if (isPreviewingHome && homeFile.exists()) homeFile else lockFile
            // 保底：如果目标不存在，找另一张
            val finalFile = if (targetFile.exists()) targetFile else if (homeFile.exists()) homeFile else null
            
            loadImageToView(finalFile, binding.ivFront)
        }
    }
    
    /**
     * 🖼️ 辅助：加载图片到ImageView
     */
    private fun loadImageToView(file: File?, imageView: ImageView) {
        if (file != null && file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageDrawable(null)
        }
    }

    /**
     * ✨ 核心：堆叠切换动画 (Card Swap Animation)
     */
    private fun animateStackSwap() {
        val front = binding.cardFront
        val back = binding.cardBack
        
        // 1. 前卡下沉动画
        front.animate()
            .translationY(50f) // 下移
            .scaleX(0.9f).scaleY(0.9f) // 变小
            .alpha(0.5f) // 变淡
            .setDuration(200)
            .start()
            
        // 2. 后卡上浮动画
        back.animate()
            .translationY(0f) // 归位
            .scaleX(1.0f).scaleY(1.0f) // 变大
            .alpha(1.0f) // 变实
            .setDuration(200)
            .withEndAction {
                // 3. 动画结束后，切换数据状态
                isPreviewingHome = !isPreviewingHome
                loadPreview() // 重新加载数据 (这时候 Front 变成了新的图)
                
                // 4. 瞬间复位 View 属性 (因为 loadPreview 已经把正确的图放到了 Front)
                front.translationY = 0f
                front.scaleX = 1.0f
                front.scaleY = 1.0f
                front.alpha = 1.0f
                
                // back会自动处理，因为我们在loadPreview后它保持在"背面"的状态
            }
            .start()
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
            "HOME_STATE" to homeState, // 传 Int
            "LOCK_STATE" to lockState  // 传 Int
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
                        // 任务完成后，默认切回 Home 预览，并刷新
                        isPreviewingHome = true 
                        loadPreview()
                    } else {
                        Toast.makeText(this, "Network Error >_<", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }
    
    /**
     * 🖼️ 立即应用壁纸到指定目标
     */
    private fun applyCurrentToTarget(flag: Int, isHome: Boolean) {
        val homeFile = File(filesDir, "wallpaper_home.png")
        val lockFile = File(filesDir, "wallpaper_lock.png")
        
        val sourceFile = if (isHome) {
            homeFile
        } else {
            // Lock: 如果 lockFile 存在就用它，否则用 homeFile（同步模式）
            if (lockFile.exists()) lockFile else homeFile
        }
        
        if (!sourceFile.exists()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                val wm = WallpaperManager.getInstance(this@MainActivity)
                wm.setBitmap(bitmap, null, true, flag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 🗂️ 历史备份
     */
    private fun backupCurrentToHistory() {
        val currentFile = File(filesDir, "wallpaper_home.png")
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
     * 🔙 撤销壁纸
     */
    private fun undoWallpaper() {
        if (historyStack.isNotEmpty()) {
            val historyFile = File(filesDir, historyStack.pop())
            if (historyFile.exists()) {
                val homeFile = File(filesDir, "wallpaper_home.png")
                historyFile.copyTo(homeFile, overwrite = true)
                
                // 恢复时简单处理：删除独立的 Lock，回归同步
                val lockFile = File(filesDir, "wallpaper_lock.png")
                if (lockFile.exists()) lockFile.delete()
                
                loadPreview()
                Toast.makeText(this, "Restored! 🔙", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 💾 保存到相册
     */
    private fun saveCurrentToGallery() {
        // 默认保存当前预览的图片
        val targetFile = if (isPreviewingHome) {
            File(filesDir, "wallpaper_home.png")
        } else {
            File(filesDir, "wallpaper_lock.png")
        }
        
        if (!targetFile.exists()) return
        
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ACG_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ACGWallpaper")
        }
        
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it).use { out ->
                FileInputStream(targetFile).copyTo(out!!)
            }
            val msg = if (isPreviewingHome) "Home saved! 🏠" else "Lock saved! 🔒"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
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
     * 📍 添加标签气泡
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
            bounceAnimate(it)
            val newState = !tagsMap[tagText]!!
            tagsMap[tagText] = newState
            chip.isChecked = newState
            updateChipStyle(chip, newState)
            saveTagsToPrefs()
            
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
     * 🎨 Chip 样式更新
     */
    private fun updateChipStyle(chip: Chip, isStrict: Boolean) {
        if (isStrict) {
            chip.chipBackgroundColor = ColorStateList.valueOf(getColor(R.color.soft_pink))
            chip.setTextColor(Color.WHITE)
            chip.chipStrokeWidth = 0f
            chip.closeIconTint = ColorStateList.valueOf(Color.WHITE)
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
            chip.setTextColor(Color.GRAY)
            chip.chipStrokeColor = ColorStateList.valueOf(Color.LTGRAY)
            chip.chipStrokeWidth = 2f
            chip.closeIconTint = ColorStateList.valueOf(Color.GRAY)
        }
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
            "HOME_STATE" to homeState,
            "LOCK_STATE" to lockState
        )
        
        val requestBuilder = if (scheduleIndex == 0) {
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
}
