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
    
    // ✨ Ultra Expanded Otaku Corpus (150+ Deep Cuts) ✨
    // 包含：萌系、日常、冷门神作、游戏梗、程序员梗
    private val mascotQuotes = listOf(
        // --- 🌅 Daily Greetings (元气满满) ---
        "Ohiyo! Master~ ☀️", "Good morning! Ready to code? ☕", "Welcome back! Missed you! 💖",
        "A fresh start awaits! 🌱", "Let's make today amazing! ✨", "System online! Hello! 🤖",
        "Happy to see you again! 😊", "The world is beautiful today! 🌸", "Yahallo~ 👋",
        "Konnichiwa! 🍱", "Konbanwa! Time to relax? 🌙", "Otsukare~ (Good work!) 🍵",
        "It's a perfect day for anime! 📺", "Did you sleep well? 🛌",

        // --- 🍭 Moe & Catchphrases (萌系口癖) ---
        "Nyanpasu~ (Non Non Biyori) 👋", "Tuturu~ 🕰️", "Uguu~ (Kanon) 🎒",
        "Auau... (Higurashi) 🥺", "Nipah~ ☆", "Gao~ (Air) 🦖",
        "Hawawa~ 💦", "Hau~ (Omochikaeri!) 🛍️", "Pyon pyon~ (Gochiusa) 🐰",
        "Moe moe kyun! 🫶", "Tehepero~ 😋", "Waku waku! (Spy x Family) ⭐",
        "Fuee~ 😵", "Pikya! ⚡", "Hae~? 🤔", "Nyaa~ 🐱", "Wan! 🐶",
        "Cheerio! (Katanagatari) 👊", "Poi? (Kancolle) ⚓", "Nano desu! 🥕",
        "Desu wa! 🌹", "Ara ara~ 🤭", "Umu! 👑", "Poyon~",

        // --- 🏫 Slice of Life & Classics (日常/经典) ---
        "Fun things are fun! (K-On!) 🎸", "Mio-chan is shy~ 😳", "Rice is a side dish! 🍚",
        "Kininarimasu! (I'm curious!) 🌳", "Eru-chan is watching... 👀",
        "Selamat Pagi! (Nichijou) 🦌", "Safe? Out? Safe! ⚾", "Nano-chan! 🔑",
        "Timotei~ Timotei~ (Lucky Star) 🚿", "I buy sausage! 🌭",
        "Rin-chan! (Yuru Camp) ⛺", "Secret Society BLANKET. 🛌", "Curry noodles at night... 🍜",
        "Explosion!! (Konosuba) 💥", "Eris pads her chest. 🍎", "Kazuma desu. 😑",
        "El Psy Kongroo. (Steins;Gate) 📱", "I am mad scientist! 👨‍🔬", "Daga otoko da. (But he's a guy) ⛩️",
        "Just according to keikaku. (TN: keikaku means plan) 📝",
        "The moon is beautiful, isn't it? 🌙", "I want to eat your pancreas. 🌸",
        "Menma, we found you! 🌼", "Dango Dango Dango~ 🍡",
        "Zettai Ryouiki is justice! (Absolute Territory) 🦵", "Flat is justice! 📏",
        "Megane (Glasses) helps. 👓", "Twintails are aerodynamics! 👧",

        // --- 🕵️‍♀️ Niche & Cult (冷门/深度梗) ---
        "Pipiru piru piru pipiru pi~ (Dokuro-chan) 🔨",
        "Ask not the sparrow how the eagle soars. (Kill la Kill) ✂️",
        "Don't lose your way! 🌟",
        "Let's all love Lain. 🌐", "Present day, present time. 🖥️",
        "Zetsubou shita! (Sayonara Zetsubou Sensei) 😵",
        "Humanity has declined. 🧚", "Watashi wa pan desu. (I am bread) 🍞",
        "Panzer vor! (Girls und Panzer) 🚜", "Tanks are cute! 🛡️",
        "Balsa the Spear. 🗡️", "Ginko was here. (Mushishi) 🌿",
        "Odd Taxi? 🚕", "Keep your hands off Eizouken! ✏️",
        "Ping Pong is life. 🏓", "Bocchi the Rock! 🎸", "Social anxiety overload... 📦",
        "Dekomori desu! 🔥", "Wicked Lord Shingan! 👁️",
        "Ai yo! (Starlight) ⭐", "Kira~ Kira~ Doki Doki! 💫",

        // --- 🎮 Gaming & Gacha (游戏/抽卡) ---
        "RNG favors the brave! 🎲", "Gacha luck +100! 🍀", "Critical hit! 💥",
        "Achievement unlocked! 🏆", "Level up! ⬆️", "New quest available! 📜",
        "Inventory full! 🎒", "Save point reached! 💾", "Boss music starts... 🎵",
        "Respawning in 3... 2... 1... ⏳", "GG WP! 🎮", "Rush B! 💣",
        "Praise the Sun! ☀️", "You died. 💀", "Hey, you. You're finally awake. 🌲",
        "Doktah? (Arknights) 💉", "Tabibito-san? (Genshin) ✨", "Kanchou? (Honkai) 🚀",
        "Sensei? (Blue Archive) 📘", "Producer-san? (Idolmaster) 🎤",

        // --- 💻 Tech & Geek (程序员梗) ---
        "No bugs today, please! 🐞", "Compiling happiness... ⏳", "Git push your dreams! ⬆️",
        "Coffee: 100%. Energy: 100%. ☕", "404: Sadness not found. 🚫",
        "Sudo make me a sandwich. 🥪", "Hello World! 🌍", "Refreshing cache... 🔄",
        "Strict mode is best mode. 🔒", "while(alive) { code(); } 💻",
        "Error 418: I'm a teapot. 🫖", "Debugging life... 🔍",
        "Stack overflow? Stack hugs! 🤗", "const happiness = true; 💛",
        "Blue mode is cool! 💙", "Pink mode is cute! 💖",

        // --- 💬 Interactions (互动/打破第四面墙) ---
        "Pat pat~ (｡•̀ᴗ-)✧", "Meow? 🐱", "Cheer up! 🌈",
        "Loading cuteness... [||||||] 100%", "Hugs incoming! 🫂", "*Stares politely* 😶",
        "Boop! 👆", "Zzz... 💤", "*Nods enthusiastically* 👀",
        "Are you ignoring me? 🥺", "I'm watching you... in a cute way! 👁️",
        "Don't shake the phone! 😵‍💫", "Battery low? Recharge with anime! 🔋",
        "Remember to rest! 😴", "Have you eaten? 🍙", "Stretch a bit! 🧘",
        "You deserve a break! 🍵", "Self-care is important! 💆",
        "It's okay to take it slow~ 🐌", "You're not alone! 👭",
        "Sending virtual hugs! 💌", "You matter! 💖", "Be kind to yourself! 🌸",
        "*Spins around* 💫", "*Waves* 👋", "Chu~ 💋", "Ehehe~ 😊"
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
        
        // --- 🎯 预览卡片叠排切换 ---
        binding.ivPreview.setOnClickListener {
            val homeFile = File(filesDir, "wallpaper_home.png")
            val lockFile = File(filesDir, "wallpaper_lock.png")
            
            // 只有当两个文件都存在时，才允许切换
            if (homeFile.exists() && lockFile.exists()) {
                bounceAnimate(binding.cardPreview)
                isPreviewingHome = !isPreviewingHome
                loadPreview()
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
    private fun loadPreview() {
        val homeFile = File(filesDir, "wallpaper_home.png")
        val lockFile = File(filesDir, "wallpaper_lock.png")
        
        // 决定要显示哪张图
        val targetFile = if (isPreviewingHome) homeFile else lockFile
        
        // 如果想看锁屏但文件不存在，自动回退到 Home
        val finalFile = if (targetFile.exists()) targetFile else if (homeFile.exists()) homeFile else null
        
        if (finalFile != null) {
            val bitmap = BitmapFactory.decodeFile(finalFile.absolutePath)
            binding.ivPreview.setImageBitmap(bitmap)
            binding.ivPreview.alpha = 0f
            binding.ivPreview.animate().alpha(1f).duration = 500
        }
        
        // 更新指示器 UI
        if (homeFile.exists() && lockFile.exists()) {
            // 双图模式：显示指示器
            binding.chipViewIndicator.visibility = View.VISIBLE
            if (isPreviewingHome) {
                binding.chipViewIndicator.text = "Home 🏠"
                binding.chipViewIndicator.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FF80AB"))
            } else {
                binding.chipViewIndicator.text = "Lock 🔒"
                binding.chipViewIndicator.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#64B5F6"))
            }
        } else {
            // 单图模式：隐藏指示器
            binding.chipViewIndicator.visibility = View.GONE
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
