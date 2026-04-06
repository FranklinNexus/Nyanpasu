package com.kuroshimira.nyanpasu

import android.Manifest
import android.app.WallpaperManager
import android.util.Log
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val FN_BUFFER_A = "wallpaper_buffer_a.png"
        private const val FN_BUFFER_B = "wallpaper_buffer_b.png"
        private const val FN_LEGACY_BUFFER = "wallpaper_buffer.png"
    }

    private lateinit var binding: ActivityMainBinding
    private val tagsMap = mutableMapOf<String, Boolean>()
    private val historyStack = java.util.ArrayDeque<String>()
    
    private val scheduleOptions = arrayOf("Daily 7:00 AM", "Every 6 Hours", "Every 12 Hours", "Every 24 Hours")
    private var homeState = 1
    private var lockState = 0
    private var isPreviewingHome = true
    
    // --- 🔥 V27.0 口味选择器 ---
    private var r18Mode = 0 // 0=Pure, 1=NSFW, 2=Mix
    
    // --- 🤖 看板娘 & 彩蛋系统 ---
    private var logoClickCount = 0
    private val logoResetHandler = Handler(Looper.getMainLooper())
    private val speechHandler = Handler(Looper.getMainLooper())
    private var lastClickTime = 0L
    private val CLICK_INTERVAL = 1500L
    
    // --- 🔑 权限请求码 ---
    private val PERMISSION_REQUEST_CODE = 101
    
    // --- 🎯 Tag 触发台词映射 ---
    private val tagResponses = mapOf(
        "blue_archive" to "Sensei! Welcome back! (๑˃ᴗ˂)ﻭ",
        "ba" to "Sensei, I've been waiting for you! (´｡• ᵕ •｡`) ♡",
        "genshin" to "Ad astra abyssosque! Let's explore Teyvat! ✨",
        "genshin_impact" to "Traveler, ready for adventure? (◕‿◕✿)",
        "arknights" to "Dokutah, focus on the mission! (•̀ᴗ•́)و",
        "ak" to "Doctor, time for sanity potion~ (๑╹ᆺ╹๑)",
        "touhou" to "Welcome to Gensokyo! (￣▽￣)ノ",
        "東方" to "Reimu is on the way... (´･ᴗ･`)",
        "fate" to "Saber-class Servant, ready! (｀・ω・´)ゞ",
        "fgo" to "Master, your command? (๑•̀ㅂ•́)و✧",
        "azur_lane" to "Commander, sortie time! ヾ(•ω•`)o",
        "al" to "Shikikan, mission briefing! (๑˃̵ᴗ˂̵)",
        "honkai" to "Captain on the bridge! (ﾉ◕ヮ◕)ﾉ*:･ﾟ✧",
        "nikke" to "Commander, reporting for duty! ୧(＾ 〰 ＾)୨",
        "kancolle" to "Admiral, fleet assembled! (•̀o•́)ง",
        "maid" to "Master, may I serve you today? (o^▽^o)",
        "catgirl" to "Nya nya~ Meow for you! (=^･ω･^=)",
        "neko" to "Kawaii cat ears detected! (=ＴェＴ=)",
        "loli" to "FBI OPEN UP! (╬ Ò﹏Ó) ...Just kidding~ Legal content only!",
        "waifu" to "Ah, a person of culture! (￣ω￣)",
        "anime" to "Great taste in Japanese animation! (☆ω☆)",
        "kawaii" to "Moe moe kyun~! ♡(˃͈ દ ˂͈ ༶ )",
        "white_hair" to "Ah yes, the superior hair color! (๑˃̵ᴗ˂̵)و",
        "pink_hair" to "Pink is justice! (｡♥‿♥｡)",
        "oppai" to "Ah, I see you're cultured too~ (¬‿¬)",
        "pantsu" to "Degenerates like you belong on... wait, you're fine! (≧▽≦)"
    )

    // ✨ 完整语料库（200+句保留）
    private val mascotQuotes = listOf(
        // 日常问候
        "Nyanpasu~ 👋", "Ohiyo! (｡･ω･｡)", "おかえりなさい！", "Yahallo~", "Konbanwa~",
        "Otsukare-sama desu!", "Selamat Pagi!", "Konnichiwa (´｡• ᵕ •｡`)",
        
        // 萌系口癖
        "Tuturu~ ♪", "Uguu~", "Auau...", "Nipah~ ☆", 
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
        
        // ✨✨✨ V24.0 核心：启动时计算屏幕比例并应用 ✨✨✨
        setupPreviewAspectRatio()
        
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        
        // 恢复状态
        homeState = prefs.getInt("HOME_STATE", 1)
        lockState = prefs.getInt("LOCK_STATE", 0)
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        updateKaomoji(binding.seekBarStyle.progress)
        
        // ✨ V27.0 恢复口味模式
        r18Mode = prefs.getInt("R18_MODE", 0) // 默认纯净模式
        when (r18Mode) {
            0 -> binding.toggleGroupMode.check(R.id.btnModeSafe)
            1 -> binding.toggleGroupMode.check(R.id.btnModeR18)
            2 -> binding.toggleGroupMode.check(R.id.btnModeMix)
        }
        
        val savedScheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
        binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[savedScheduleIndex]} ▾"
        updateToggleButtons()
        
        // 恢复标签
        val savedTagsSet = prefs.getStringSet("SAVED_TAGS_V2", emptySet()) ?: emptySet()
        savedTagsSet.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) addChipToGroup(parts[0], parts[1].toBoolean())
            else addChipToGroup(entry, false)
        }

        migrateLegacyPrefetchIfNeeded()

        // 加载预览
        loadPreview()

        // --- 🎯 核心交互 ---
        
        // Logo 点击 (说话 + 彩蛋)
        binding.ivLogo.setOnClickListener {
            bounceAnimate(it)
            handleLogoClick()
        }

        // 预览图点击 (切换)
        binding.ivPreview.setOnClickListener {
            val homeFile = File(filesDir, "wallpaper_home.png")
            val lockFile = File(filesDir, "wallpaper_lock.png")
            val isDualMode = (homeState != lockState) && (homeState > 0 && lockState > 0)
            
            if (isDualMode && homeFile.exists() && lockFile.exists()) {
                binding.ivPreview.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                isPreviewingHome = !isPreviewingHome
                
                // 淡入淡出切换
                binding.ivPreview.animate().alpha(0.5f).setDuration(100).withEndAction {
                    loadPreview()
                    binding.ivPreview.animate().alpha(1f).setDuration(100).start()
                }.start()
            } else {
                bounceAnimate(binding.previewCard)
            }
        }

        // 其他控件绑定
        binding.tvScheduleInfo.setOnClickListener { showScheduleDialog() }
        
        binding.seekBarStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateKaomoji(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("STYLE", seekBar?.progress ?: 50).apply()
                refreshAutoWallpaperWorkIfNeeded()
            }
        })
        
        binding.etTagInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
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
        
        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DAILY_ENABLED", isChecked).apply()
            if (isChecked) {
                if (homeState == 0 && lockState == 0) {
                    Toast.makeText(
                        this,
                        "Turn on Home or Lock wallpaper first — auto mode only applies when at least one is on.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                setupPeriodicWork()
            } else {
                cancelPeriodicWork()
            }
        }
        
        // --- 🔥 V27.0 口味选择器监听 ---
        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                // 震动反馈
                binding.toggleGroupMode.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                
                when (checkedId) {
                    R.id.btnModeSafe -> {
                        r18Mode = 0
                        speak("Safe mode engaged! 🛡️")
                    }
                    R.id.btnModeMix -> {
                        r18Mode = 2
                        speak("Mixing things up! (⁄ ⁄•⁄ω⁄•⁄ ⁄)⁄")
                    }
                    R.id.btnModeR18 -> {
                        r18Mode = 1
                        speak("Ahem... Adult mode on. 🔥")
                    }
                }
                // 保存设置
                prefs.edit().putInt("R18_MODE", r18Mode).apply()
                
                refreshAutoWallpaperWorkIfNeeded()
            }
        }
        
        // --- 🚀 V24.0 极速刷新逻辑 ---
        binding.btnUpdate.setOnClickListener {
            bounceAnimate(it)
            val ready = firstReadyPrefetchFile()
            if (ready != null) {
                applyPrefetchToHome(ready)
                Toast.makeText(this, "Instant Load! ⚡", Toast.LENGTH_SHORT).show()
                refillEmptyPrefetchSlots()
            } else {
                startOneTimeWork(isUrgent = true)
            }
        }
        
        binding.btnToggleHome.setOnClickListener {
            bounceAnimate(it)
            homeState = (homeState + 1) % 3
            prefs.edit().putInt("HOME_STATE", homeState).apply()
            updateToggleButtons()
            refreshAutoWallpaperWorkIfNeeded()
            if (homeState > 0) applyCurrentVisibleToTarget(WallpaperManager.FLAG_SYSTEM)
        }
        
        binding.btnToggleLock.setOnClickListener {
            bounceAnimate(it)
            lockState = (lockState + 1) % 3
            prefs.edit().putInt("LOCK_STATE", lockState).apply()
            updateToggleButtons()
            refreshAutoWallpaperWorkIfNeeded()
            if (lockState > 0) applyCurrentVisibleToTarget(WallpaperManager.FLAG_LOCK)
        }
        
        binding.btnUndo.setOnClickListener { bounceAnimate(it); undoWallpaper() }
        binding.btnSave.setOnClickListener { bounceAnimate(it); saveCurrentToGallery() }

        // --- 🚀 启动初始化 ---
        
        // 1. 清理旧历史文件（超过7天）
        cleanOldHistoryFiles()
        
        // 2. 请求必要权限
        requestStoragePermission()
        
        // 3. 首次启动引导
        showWelcomeDialogIfNeeded()
        
        // 4. 自动换壁纸：冷启动时必须重新 enqueue，否则 WorkManager 里仍是旧的 InputData（标签/主锁屏状态），且重装/清数据后开关为开时从未注册过任务
        refreshAutoWallpaperWorkIfNeeded()

        // 5. 启动看板娘系统（强制问候）
        Handler(Looper.getMainLooper()).postDelayed({
            speak("Nyanpasu~ (〃＾▽＾〃) 👋") // 启动首句，带颜文字
            scheduleRandomSpeech() // 启动随机闲聊循环
        }, 800)
    }

    /**
     * 自动周期任务（若开启）与预取槽一并刷新，保证 Tag/滑条/R18 变化后后台正在下的图仍是当前偏好。
     */
    private fun refreshAutoWallpaperWorkIfNeeded() {
        if (!this::binding.isInitialized) return
        if (binding.switchDaily.isChecked) setupPeriodicWork()
        refillEmptyPrefetchSlots()
    }

    // --- 🤖 看板娘智能系统 ---

    // 随机闲聊任务（递归循环）
    private val randomSpeechRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                // 只在空闲时说话
                if (binding.tvMascotSpeech.alpha == 0f) {
                    showRandomQuote()
                }
                // 下次触发：30~60秒随机
                val delay = Random.nextLong(30000, 60000)
                speechHandler.postDelayed(this, delay)
            }
        }
    }

    private fun scheduleRandomSpeech() {
        speechHandler.removeCallbacks(randomSpeechRunnable)
        // 首次延迟10~30秒
        val delay = Random.nextLong(10000, 30000)
        speechHandler.postDelayed(randomSpeechRunnable, delay)
    }
    
    // 强制说话（带自动消失）
    private fun speak(text: String) {
        binding.tvMascotSpeech.text = text
        
        // 取消旧任务
        speechHandler.removeCallbacksAndMessages(null)
        
        // 淡入
        binding.tvMascotSpeech.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        
        // 4秒后淡出
        speechHandler.postDelayed({
            binding.tvMascotSpeech.animate()
                .alpha(0f)
                .setDuration(800)
                .withEndAction {
                    // 淡出完成后重启随机闲聊
                    scheduleRandomSpeech()
                }
                .start()
        }, 4000)
    }

    private fun showRandomQuote() {
        speak(mascotQuotes.random())
    }

    // Logo 点击处理（彩蛋触发）
    private fun handleLogoClick() {
        logoClickCount++
        
        if (logoClickCount >= 10) {
            showDeveloperDialog()
            logoClickCount = 0
            return
        }
        
        logoResetHandler.removeCallbacksAndMessages(null)
        logoResetHandler.postDelayed({ logoClickCount = 0 }, 2000)
        
        // 点击立刻说话
        showRandomQuote()
    }
    
    // --- 🥚 彩蛋：开发者名片 & 丝带爆炸 ---
    
    private fun showDeveloperDialog() {
        // 1. 先放丝带特效
        fireConfetti()
        
        // 2. 精致的名片弹窗
        val message = """
            (｡♥‿♥｡) Hello there!
            Thanks for using Nyanpasu~
            
            Made with ❤ by [KuroshiMira]
            A veteran otaku building dreams!
            Let's grab a coffee and be friends? (〃￣ω￣〃)
            
            Project: Nyanpasu Wallpaper
            Version: 1.0.0 (Initial Release)
            
            (✧ω✧) Enjoy your moe journey!
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Developer (´ ε ` )")
            .setMessage(message)
            .setPositiveButton("My Blog") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://WisdomEchoes.net")))
            }
            .setNegativeButton("Contact Me") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/FranklinNexus")))
                } catch (e: Exception) {
                    Toast.makeText(this, "Telegram not found (ノдヽ)", Toast.LENGTH_SHORT).show()
                }
            }
            .setIcon(R.mipmap.ic_launcher)
            .show()
    }
    
    // ✨ 纯代码丝带爆炸特效 (50个彩色方块从顶部掉落)
    private fun fireConfetti() {
        val container = binding.confettiContainer
        val colors = listOf(
            Color.parseColor("#FF80AB"), // Pink
            Color.parseColor("#64B5F6"), // Blue
            Color.parseColor("#FFD54F"), // Yellow
            Color.parseColor("#81C784"), // Green
            Color.parseColor("#FF8A65")  // Orange
        )
        
        // 等容器布局完成
        container.post {
            for (i in 0..50) {
                val confetti = View(this)
                confetti.setBackgroundColor(colors.random())
                
                val size = Random.nextInt(10, 25)
                val params = FrameLayout.LayoutParams(size, size)
                
                // 随机起始X位置
                params.leftMargin = Random.nextInt(0, container.width - size)
                params.topMargin = -size
                
                container.addView(confetti, params)
                
                // 下落动画
                confetti.animate()
                    .translationY((container.height + 200).toFloat())
                    .rotation(Random.nextInt(0, 360).toFloat() * 5)
                    .setDuration(Random.nextLong(1500, 3000))
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { container.removeView(confetti) }
                    .start()
            }
        }
    }

    // --- 📦 标准辅助方法 ---
    
    // --- 📏 V24.0 强制预览框比例 = 屏幕比例 ---
    private fun setupPreviewAspectRatio() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        
        // 构建比例字符串，例如 "1080:2400"
        // 这样 CardView 就会严格按照手机屏幕形状显示
        val ratioString = "$screenWidth:$screenHeight"
        
        val params = binding.previewCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.dimensionRatio = ratioString
        binding.previewCard.layoutParams = params
    }
    
    // --- 🎨 V24.0 修复颜色同步逻辑 ---
    private fun loadPreview() {
        val homeFile = File(filesDir, "wallpaper_home.png")
        val lockFile = File(filesDir, "wallpaper_lock.png")
        
        binding.ivPreview.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        
        val isDualMode = (homeState != lockState) && (homeState > 0 && lockState > 0)
        val targetFile = if (isPreviewingHome) homeFile else lockFile
        val finalFile = if (targetFile.exists()) targetFile else if (homeFile.exists()) homeFile else null
        
        if (finalFile != null) {
            val bitmap = BitmapFactory.decodeFile(finalFile.absolutePath)
            setPreviewBitmap(bitmap)
        } else {
            clearPreviewBitmap()
        }
        
        // ✨✨✨ 颜色同步修复区 ✨✨✨
        if (isDualMode) {
            binding.tvViewIndicator.visibility = View.VISIBLE
            
            // 定义颜色
            val pinkColor = ColorStateList.valueOf(getColor(R.color.brand_pink))
            val blueColor = ColorStateList.valueOf(getColor(R.color.brand_blue))
            
            if (isPreviewingHome) {
                binding.tvViewIndicator.text = "Home Screen"
                // 逻辑修复：如果 Home 按钮是蓝色(2)，标签就是蓝色；否则(1)是粉色
                binding.tvViewIndicator.backgroundTintList = if (homeState == 2) blueColor else pinkColor
            } else {
                binding.tvViewIndicator.text = "Lock Screen"
                // 逻辑修复：如果 Lock 按钮是蓝色(2)，标签就是蓝色；否则(1)是粉色
                binding.tvViewIndicator.backgroundTintList = if (lockState == 2) blueColor else pinkColor
            }
        } else {
            binding.tvViewIndicator.visibility = View.GONE
        }
    }

    private fun bounceAnimate(view: View) {
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(OvershootInterpolator()).start()
    }

    private fun updateToggleButtons() {
        val pink = ColorStateList.valueOf(getColor(R.color.brand_pink))
        val blue = ColorStateList.valueOf(getColor(R.color.brand_blue))
        val gray = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        
        when (homeState) {
            1 -> setupButton(binding.btnToggleHome, pink, R.drawable.ic_check_bold, Color.WHITE)
            2 -> setupButton(binding.btnToggleHome, blue, R.drawable.ic_check_bold, Color.WHITE)
            else -> setupButton(binding.btnToggleHome, gray, android.R.drawable.checkbox_off_background, Color.GRAY)
        }
        
        when (lockState) {
            1 -> setupButton(binding.btnToggleLock, pink, R.drawable.ic_check_bold, Color.WHITE)
            2 -> setupButton(binding.btnToggleLock, blue, R.drawable.ic_check_bold, Color.WHITE)
            else -> setupButton(binding.btnToggleLock, gray, android.R.drawable.checkbox_off_background, Color.GRAY)
        }
        
        loadPreview()
    }
    
    private fun setupButton(
        button: com.google.android.material.button.MaterialButton,
        tint: ColorStateList,
        icon: Int,
        textColor: Int
    ) {
        button.backgroundTintList = tint
        button.setTextColor(textColor)
        button.iconTint = ColorStateList.valueOf(textColor)
        button.setIconResource(icon)
    }

    /** 旧版单文件缓冲迁移到 A 槽，避免已装用户丢「预取图」。 */
    private fun migrateLegacyPrefetchIfNeeded() {
        val leg = File(filesDir, FN_LEGACY_BUFFER)
        if (!leg.exists() || leg.length() == 0L) return
        val a = File(filesDir, FN_BUFFER_A)
        if (!a.exists() || a.length() == 0L) {
            leg.copyTo(a, overwrite = true)
        }
        leg.delete()
    }

    /** 优先取最新写入的预取槽，提高连点刷新时命中「已下好的一张」。 */
    private fun firstReadyPrefetchFile(): File? {
        migrateLegacyPrefetchIfNeeded()
        val ready = listOf(File(filesDir, FN_BUFFER_A), File(filesDir, FN_BUFFER_B))
            .filter { it.exists() && it.length() > 0 }
        if (ready.isEmpty()) return null
        return ready.maxByOrNull { it.lastModified() }
    }

    /** 对空槽立刻各排一队预取（无 1s 人为延迟）；双槽可并行缩小「秒开 → 要等网」的间隔。 */
    private fun refillEmptyPrefetchSlots() {
        migrateLegacyPrefetchIfNeeded()
        for (slot in listOf("a", "b")) {
            val f = File(filesDir, if (slot == "b") FN_BUFFER_B else FN_BUFFER_A)
            if (!f.exists() || f.length() == 0L) {
                startOneTimeWork(isUrgent = false, prefetchSlot = slot)
            }
        }
    }

    private suspend fun applyWallpaperBitmap(which: Int, bitmap: Bitmap): Boolean {
        val safe = ImageProcessor.forWallpaperManager(bitmap)
        return try {
            withContext(Dispatchers.Main) {
                if (isFinishing) return@withContext false
                WallpaperManager.getInstance(this@MainActivity).setBitmap(safe, null, true, which)
                true
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "setBitmap failed which=$which", e)
            false
        }
    }

    private suspend fun applyHomeLockCombinedOrSplit(bitmap: Bitmap): Boolean {
        val both = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        if (applyWallpaperBitmap(both, bitmap)) return true
        val s = applyWallpaperBitmap(WallpaperManager.FLAG_SYSTEM, bitmap)
        val l = applyWallpaperBitmap(WallpaperManager.FLAG_LOCK, bitmap)
        return s && l
    }

    private fun applyPrefetchToHome(prefetch: File) {
        val homeFile = File(filesDir, "wallpaper_home.png")
        if (!prefetch.exists() || prefetch.length() == 0L) return
        backupCurrentToHistory()
        prefetch.copyTo(homeFile, overwrite = true)
        prefetch.delete()
        isPreviewingHome = true
        loadPreview()
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(homeFile.absolutePath)
                } catch (_: OutOfMemoryError) {
                    null
                }
            }
            if (bitmap == null) {
                Log.e("MainActivity", "Failed to decode bitmap from buffer")
                return@launch
            }
            val isSyncMode = (lockState == 1) || (lockState == 2 && homeState == 2)
            try {
                if (isSyncMode && lockState > 0) {
                    withContext(Dispatchers.IO) {
                        homeFile.copyTo(File(filesDir, "wallpaper_lock.png"), overwrite = true)
                    }
                }
                val ok =
                    when {
                        isSyncMode && homeState > 0 && lockState > 0 -> applyHomeLockCombinedOrSplit(bitmap)
                        homeState > 0 ->
                            applyWallpaperBitmap(WallpaperManager.FLAG_SYSTEM, bitmap)
                        isSyncMode && lockState > 0 ->
                            applyWallpaperBitmap(WallpaperManager.FLAG_LOCK, bitmap)
                        else -> true
                    }
                if (ok) {
                    Log.d("MainActivity", "Wallpaper applied from prefetch buffer")
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "系统壁纸未更新，请检查是否允许更换壁纸或关闭系统杂志锁屏",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Wallpaper sync failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * @param isUrgent 为 false 时写入 A/B 预取槽，使用唯一任务名并行两条队列；为 true 时直连 wallpaper_home。
     */
    private fun startOneTimeWork(isUrgent: Boolean, prefetchSlot: String = "a") {
        val strictTags = tagsMap.filter { it.value }.keys.toTypedArray()
        val softTags = tagsMap.filter { !it.value }.keys.toTypedArray()
        val inputData = workDataOf(
            "STYLE_VALUE" to binding.seekBarStyle.progress,
            "STRICT_TAGS" to strictTags,
            "SOFT_TAGS" to softTags,
            "HOME_STATE" to homeState,
            "LOCK_STATE" to lockState,
            "R18_MODE" to r18Mode,
            "IS_URGENT" to isUrgent,
            "BUFFER_SLOT" to prefetchSlot,
        )

        if (isUrgent) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnUpdate.isEnabled = false
            binding.btnUpdate.text = "Summoning... ⌛"
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(this).enqueue(request)
            WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
                .observe(this) { workInfo ->
                    if (isFinishing) return@observe
                    if (workInfo != null && workInfo.state.isFinished) {
                        binding.progressBar.visibility = View.GONE
                        binding.btnUpdate.isEnabled = true
                        binding.btnUpdate.text = "Refresh ✨"
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                            isPreviewingHome = true
                            loadPreview()
                            Toast.makeText(this, "New wallpaper applied! ✨", Toast.LENGTH_SHORT).show()
                            refillEmptyPrefetchSlots()
                        } else {
                            Toast.makeText(
                                this,
                                "下载失败或系统拒绝设置壁纸。请检查网络、更换壁纸权限；华为等机型请关闭杂志锁屏。",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
        } else {
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(this).enqueueUniqueWork(
                "prefetch_wallpaper_$prefetchSlot",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
    
    private fun saveTagsToPrefs() {
        val set = tagsMap.map { "${it.key}|${it.value}" }.toSet()
        getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE).edit().putStringSet("SAVED_TAGS_V2", set).apply()
        refreshAutoWallpaperWorkIfNeeded()
    }
    
    private fun addChipToGroup(tagText: String, isStrict: Boolean) {
        if (tagsMap.containsKey(tagText)) return
        
        // ✨ 检测特殊 Tag 触发台词
        val lowerTag = tagText.lowercase().trim().replace(" ", "_")
        val response = tagResponses[lowerTag]
        if (response != null) {
            speak(response) // 优先说出彩蛋台词
        }
        
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
            Toast.makeText(this, if(newState) "Strict Mode 🔒" else "Soft Mode 🎲", Toast.LENGTH_SHORT).show()
        }
        chip.setOnCloseIconClickListener {
            binding.chipGroupTags.removeView(chip)
            tagsMap.remove(tagText)
            saveTagsToPrefs()
        }
        binding.chipGroupTags.addView(chip)
    }

    private fun updateChipStyle(chip: Chip, isStrict: Boolean) {
        if (isStrict) {
            chip.chipBackgroundColor = ColorStateList.valueOf(getColor(R.color.brand_pink))
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
    
    private fun updateKaomoji(progress: Int) {
        val emoji = when (progress) {
            in 0..20 -> "( ˶˘ ³˘)🍬"
            in 21..40 -> "(｡•́‿•́｡)✨"
            in 80..100 -> "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)💋"
            in 60..79 -> "(¬‿¬)🍷"
            else -> "(・_・)🎲"
        }
        binding.tvStyleDesc.text = emoji
    }
    
    private fun backupCurrentToHistory() {
        val currentFile = File(filesDir, "wallpaper_home.png")
        if (currentFile.exists()) {
            val timestamp = System.currentTimeMillis()
            val backupFile = File(filesDir, "history_$timestamp.png")
            currentFile.copyTo(backupFile, overwrite = true)
            historyStack.push(backupFile.name)
            if (historyStack.size > 5) File(filesDir, historyStack.removeLast()).delete()
        }
    }
    
    private fun undoWallpaper() {
        if (historyStack.isNotEmpty()) {
            val historyFile = File(filesDir, historyStack.pop())
            if (historyFile.exists()) {
                historyFile.copyTo(File(filesDir, "wallpaper_home.png"), true)
                loadPreview()
                Toast.makeText(this, "Previous wallpaper restored! 🔙", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "History file not found", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No history available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveCurrentToGallery() {
        val file = File(filesDir, "wallpaper_home.png")
        if (!file.exists()) {
            Toast.makeText(this, "No wallpaper to save yet. Try refreshing first!", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Nyanpasu_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nyanpasu")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                contentResolver.openOutputStream(it).use { out ->
                    FileInputStream(file).copyTo(out!!)
                }
                Toast.makeText(this, "Saved to gallery! 📸", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Failed to save. Check storage permission", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed. Grant storage permission in settings", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // --- 🖼️ V22.0 修复：全屏强制适配 ---
    private fun applyCurrentVisibleToTarget(flag: Int) {
        val photoView = binding.ivPreview
        if (photoView.drawable == null) return

        lifecycleScope.launch {
            try {
                val croppedBitmap = withContext(Dispatchers.Main) {
                    getVisibleBitmap(photoView)
                }
                if (croppedBitmap == null) {
                    fallbackApplyFromFile(flag)
                    return@launch
                }
                val displayMetrics = resources.displayMetrics
                val finalBitmap = withContext(Dispatchers.Default) {
                    Bitmap.createScaledBitmap(
                        croppedBitmap,
                        displayMetrics.widthPixels,
                        displayMetrics.heightPixels,
                        true
                    ).also { scaled ->
                        if (scaled !== croppedBitmap) croppedBitmap.recycle()
                    }
                }
                val ok = applyWallpaperBitmap(flag, finalBitmap)
                withContext(Dispatchers.Main) {
                    val target = if (flag == WallpaperManager.FLAG_SYSTEM) "Home" else "Lock"
                    if (ok) {
                        Toast.makeText(this@MainActivity, "$target Updated! (Perfect Fit) ✨", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "未能写入系统壁纸（$target），请检查权限或杂志锁屏设置",
                            Toast.LENGTH_LONG,
                        ).show()
                        fallbackApplyFromFile(flag)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                fallbackApplyFromFile(flag)
            }
        }
    }
    
    // ✨ 魔法方法：从 PhotoView 提取当前可见区域
    private fun getVisibleBitmap(photoView: PhotoView): Bitmap? {
        val drawable = photoView.drawable ?: return null
        val originalBitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        
        try {
            // 获取显示矩阵（图片在 View 中的实际位置）
            val displayRect = photoView.displayRect ?: return null
            
            // 计算缩放比例
            val scale = displayRect.width() / originalBitmap.width
            
            // View 的可视范围
            val viewWidth = photoView.width.toFloat()
            val viewHeight = photoView.height.toFloat()
            
            // 计算 Bitmap 中哪部分在 View 范围内
            // displayRect.left 通常是负数（图片被拖到左边）
            var left = -displayRect.left / scale
            var top = -displayRect.top / scale
            var width = viewWidth / scale
            var height = viewHeight / scale
            
            // 边界修正（防止超出 Bitmap 范围）
            if (left < 0) left = 0f
            if (top < 0) top = 0f
            if (left + width > originalBitmap.width) width = originalBitmap.width - left
            if (top + height > originalBitmap.height) height = originalBitmap.height - top
            
            // 创建裁剪后的 Bitmap
            return Bitmap.createBitmap(
                originalBitmap,
                left.toInt(),
                top.toInt(),
                width.toInt(),
                height.toInt()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    // 回退方法：直接从文件读取（如果裁剪失败）
    private fun fallbackApplyFromFile(flag: Int) {
        val homeFile = File(filesDir, "wallpaper_home.png")
        val lockFile = File(filesDir, "wallpaper_lock.png")
        // 简单的回退逻辑：根据 flag 选择对应文件
        val sourceFile = if (flag == WallpaperManager.FLAG_SYSTEM) homeFile else lockFile
        val finalFile = if (sourceFile.exists()) sourceFile else homeFile
        
        if (!finalFile.exists()) return
        
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(finalFile.absolutePath)
            } ?: return@launch
            try {
                val ok = applyWallpaperBitmap(flag, bitmap)
                withContext(Dispatchers.Main) {
                    val target = if (flag == WallpaperManager.FLAG_SYSTEM) "Home" else "Lock"
                    if (ok) {
                        Toast.makeText(this@MainActivity, "$target wallpaper applied! ✨", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "无法设置 $target 壁纸，请查看系统是否限制第三方应用",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setPreviewBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            clearPreviewBitmap()
            return
        }
        val old = (binding.ivPreview.drawable as? BitmapDrawable)?.bitmap
        binding.ivPreview.setImageBitmap(bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
    }

    private fun clearPreviewBitmap() {
        val old = (binding.ivPreview.drawable as? BitmapDrawable)?.bitmap
        binding.ivPreview.setImageDrawable(null)
        if (old != null && !old.isRecycled) old.recycle()
    }
    
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

    private fun setupPeriodicWork() {
         val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
         val style = prefs.getInt("STYLE", 50)
         val strictTags = tagsMap.filter { it.value }.keys.toTypedArray()
         val softTags = tagsMap.filter { !it.value }.keys.toTypedArray()
         val scheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
         val scheduleValues = intArrayOf(-1, 6, 12, 24)
         val constraints = Constraints.Builder()
             .setRequiredNetworkType(NetworkType.CONNECTED)
             .build()
         val inputData = workDataOf(
             "STYLE_VALUE" to style,
             "STRICT_TAGS" to strictTags,
             "SOFT_TAGS" to softTags,
             "HOME_STATE" to homeState,
             "LOCK_STATE" to lockState,
             "R18_MODE" to r18Mode,
             "IS_URGENT" to true
         )
         val requestBuilder = if (scheduleIndex == 0) {
             val delayMs = millisUntilNextClock(7, 0)
             PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
                 .setConstraints(constraints)
                 .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
         } else {
             PeriodicWorkRequestBuilder<WallpaperWorker>(
                 scheduleValues[scheduleIndex].toLong(),
                 TimeUnit.HOURS
             ).setConstraints(constraints)
         }
         WorkManager.getInstance(this).enqueueUniquePeriodicWork(
             "AUTO_JOB",
             ExistingPeriodicWorkPolicy.UPDATE,
             requestBuilder.setInputData(inputData).addTag("AUTO_WALLPAPER").build()
         )
    }

    /**
     * 距离下一次本地 [hourOfDay]:[minute] 的毫秒数（至少 1 分钟），用于「每日」首次执行时间。
     * 说明：PeriodicWork 后续间隔仍为约 24 小时，受系统电量策略影响可能略有漂移。
     */
    private fun millisUntilNextClock(hourOfDay: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_MONTH, 1)
        }
        val delta = next.timeInMillis - now.timeInMillis
        return delta.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    }
    
    private fun cancelPeriodicWork() {
        WorkManager.getInstance(this).cancelUniqueWork("AUTO_JOB")
    }
    
    // --- 🔑 权限管理 ---
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要 READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 
                    PERMISSION_REQUEST_CODE
                )
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 9 及以下需要 WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted! You can now save images ✨", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. Save feature won't work :(", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // --- 🎉 首次启动引导 ---
    
    private fun showWelcomeDialogIfNeeded() {
        val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("IS_FIRST_LAUNCH", true)
        
        if (isFirstLaunch) {
            AlertDialog.Builder(this)
                .setTitle("Welcome to Nyanpasu! 🎉")
                .setMessage(
                    "Thanks for installing!\n\n" +
                    "✨ Quick Guide:\n" +
                    "• Tap Home/Lock buttons to toggle (Off → Pink → Blue)\n" +
                    "• Pink = Sync mode (same wallpaper)\n" +
                    "• Blue = Independent mode (different wallpapers)\n" +
                    "• Slide the style bar to adjust taste\n" +
                    "• Add tags for custom preferences\n\n" +
                    "Tip: Click the logo 10 times for a surprise~ (¬‿¬)"
                )
                .setPositiveButton("Let's Go! ✨") { _, _ ->
                    prefs.edit().putBoolean("IS_FIRST_LAUNCH", false).apply()
                }
                .setCancelable(false)
                .setIcon(R.mipmap.ic_launcher)
                .show()
        }
    }
    
    // --- 🧹 文件清理 ---
    
    private fun cleanOldHistoryFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filesDir = filesDir
                val currentTime = System.currentTimeMillis()
                val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
                
                filesDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("history_")) {
                        // 解析时间戳
                        val timestamp = file.name.removePrefix("history_").removeSuffix(".png").toLongOrNull()
                        if (timestamp != null && (currentTime - timestamp) > sevenDaysInMillis) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (this::binding.isInitialized) {
            migrateLegacyPrefetchIfNeeded()
            refillEmptyPrefetchSlots()
        }
    }

    override fun onDestroy() {
        clearPreviewBitmap()
        speechHandler.removeCallbacksAndMessages(null)
        logoResetHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
