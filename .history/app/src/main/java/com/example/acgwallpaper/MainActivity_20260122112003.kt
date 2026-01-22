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
    
    // 0=Off, 1=Pink(Sync), 2=Blue(Independent)
    private var homeState = 1
    private var lockState = 0
    private var isPreviewingHome = true
    
    private var logoClickCount = 0
    private val logoResetHandler = Handler(Looper.getMainLooper())
    private val speechHideHandler = Handler(Looper.getMainLooper())
    private var lastClickTime = 0L
    private val CLICK_INTERVAL = 1500L

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
        
        homeState = prefs.getInt("HOME_STATE", 1)
        lockState = prefs.getInt("LOCK_STATE", 0)
        
        loadPreview()
        
        binding.seekBarStyle.progress = prefs.getInt("STYLE", 50)
        binding.switchDaily.isChecked = prefs.getBoolean("DAILY_ENABLED", false)
        updateKaomoji(binding.seekBarStyle.progress)
        
        val savedScheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
        binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[savedScheduleIndex]} ▾"
        updateToggleButtons()

        val savedTagsSet = prefs.getStringSet("SAVED_TAGS_V2", emptySet()) ?: emptySet()
        savedTagsSet.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) addChipToGroup(parts[0], parts[1].toBoolean())
            else addChipToGroup(entry, false)
        }

        // --- 核心修复：预览切换逻辑 (点击图片) ---
        binding.ivPreview.setOnClickListener {
            val homeFile = File(filesDir, "wallpaper_home.png")
            val lockFile = File(filesDir, "wallpaper_lock.png")
            val isDualMode = (homeState != lockState) && (homeState > 0 && lockState > 0)
            
            if (isDualMode && homeFile.exists() && lockFile.exists()) {
                // 震动反馈
                binding.ivPreview.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                
                // 切换状态
                isPreviewingHome = !isPreviewingHome
                
                // 简单的淡入淡出动画，不再移动位置
                binding.ivPreview.animate().alpha(0.5f).setDuration(100).withEndAction {
                    loadPreview() // 加载新图
                    binding.ivPreview.animate().alpha(1f).setDuration(100).start()
                }.start()
                
            } else {
                bounceAnimate(binding.previewCard)
            }
        }

        binding.ivLogo.setOnClickListener {
            bounceAnimate(it)
            handleLogoClick()
        }

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
            if (isChecked) setupPeriodicWork() else cancelPeriodicWork()
        }

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
        
        binding.btnToggleHome.setOnClickListener { 
            bounceAnimate(it)
            homeState = (homeState + 1) % 3
            prefs.edit().putInt("HOME_STATE", homeState).apply()
            updateToggleButtons()
            if (homeState > 0) applyCurrentToTarget(WallpaperManager.FLAG_SYSTEM, isHome = true)
        }
        
        binding.btnToggleLock.setOnClickListener { 
            bounceAnimate(it)
            lockState = (lockState + 1) % 3
            prefs.edit().putInt("LOCK_STATE", lockState).apply()
            updateToggleButtons()
            if (lockState > 0) applyCurrentToTarget(WallpaperManager.FLAG_LOCK, isHome = false)
        }

        binding.btnUndo.setOnClickListener { bounceAnimate(it); undoWallpaper() }
        binding.btnSave.setOnClickListener { bounceAnimate(it); saveCurrentToGallery() }
        
        // 启动时说句话
        Handler(Looper.getMainLooper()).postDelayed({
            showRandomQuote()
        }, 500)
    }

    // --- 核心修复：稳固的加载逻辑 ---
    private fun loadPreview() {
        val homeFile = File(filesDir, "wallpaper_home.png")
        val lockFile = File(filesDir, "wallpaper_lock.png")
        
        // 判断模式
        val isDualMode = (homeState != lockState) && (homeState > 0 && lockState > 0)
        
        // 决定显示哪个文件
        val targetFile = if (isPreviewingHome) homeFile else lockFile
        val finalFile = if (targetFile.exists()) targetFile else if (homeFile.exists()) homeFile else null
        
        // 加载图片
        if (finalFile != null) {
            val bitmap = BitmapFactory.decodeFile(finalFile.absolutePath)
            binding.ivPreview.setImageBitmap(bitmap)
        } else {
            binding.ivPreview.setImageDrawable(null)
        }
        
        // 更新指示器 UI
        if (isDualMode) {
            binding.tvViewIndicator.visibility = View.VISIBLE
            if (isPreviewingHome) {
                binding.tvViewIndicator.text = "Home Screen"
                binding.tvViewIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF80AB"))
            } else {
                binding.tvViewIndicator.text = "Lock Screen"
                binding.tvViewIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#64B5F6"))
            }
        } else {
            binding.tvViewIndicator.visibility = View.GONE
        }
    }
    
    private fun handleLogoClick() {
        logoClickCount++
        if (logoClickCount >= 10) {
            showDeveloperDialog()
            logoClickCount = 0
            return
        }
        logoResetHandler.removeCallbacksAndMessages(null)
        logoResetHandler.postDelayed({ logoClickCount = 0 }, 2000)
        showRandomQuote()
    }

    private fun showRandomQuote() {
        val quote = mascotQuotes.random()
        binding.tvMascotSpeech.text = quote
        
        binding.tvMascotSpeech.alpha = 0f
        binding.tvMascotSpeech.animate()
            .alpha(1f)
            .setDuration(500)
            .withEndAction {
                speechHideHandler.removeCallbacksAndMessages(null)
                speechHideHandler.postDelayed({
                    binding.tvMascotSpeech.animate()
                        .alpha(0f)
                        .setDuration(800)
                        .start()
                }, 4000)
            }
            .start()
    }

    private fun showDeveloperDialog() {
        val blogUrl = "https://github.com/YourUsername" 
        AlertDialog.Builder(this)
            .setTitle("👨‍💻 Developer")
            .setMessage("Hi! Thanks for using ACG Daily.\n\nCode with ❤️ by [Your Name].\n\nVisit my blog?")
            .setPositiveButton("Visit") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(blogUrl)))
            }
            .setNeutralButton("Close", null)
            .show()
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
        
        // Home
        if (homeState == 1) { // Pink
            binding.btnToggleHome.backgroundTintList = pink
            binding.btnToggleHome.setTextColor(Color.WHITE)
            binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnToggleHome.setIconResource(R.drawable.ic_check_bold)
        } else if (homeState == 2) { // Blue
            binding.btnToggleHome.backgroundTintList = blue
            binding.btnToggleHome.setTextColor(Color.WHITE)
            binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnToggleHome.setIconResource(R.drawable.ic_check_bold)
        } else { // Off
            binding.btnToggleHome.backgroundTintList = gray
            binding.btnToggleHome.setTextColor(Color.GRAY)
            binding.btnToggleHome.iconTint = ColorStateList.valueOf(Color.GRAY)
            binding.btnToggleHome.setIconResource(android.R.drawable.checkbox_off_background)
        }
        
        // Lock
        if (lockState == 1) { // Pink
            binding.btnToggleLock.backgroundTintList = pink
            binding.btnToggleLock.setTextColor(Color.WHITE)
            binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnToggleLock.setIconResource(R.drawable.ic_check_bold)
        } else if (lockState == 2) { // Blue
            binding.btnToggleLock.backgroundTintList = blue
            binding.btnToggleLock.setTextColor(Color.WHITE)
            binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnToggleLock.setIconResource(R.drawable.ic_check_bold)
        } else { // Off
            binding.btnToggleLock.backgroundTintList = gray
            binding.btnToggleLock.setTextColor(Color.GRAY)
            binding.btnToggleLock.iconTint = ColorStateList.valueOf(Color.GRAY)
            binding.btnToggleLock.setIconResource(android.R.drawable.checkbox_off_background)
        }
        
        loadPreview()
    }

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
            "HOME_STATE" to homeState,
            "LOCK_STATE" to lockState
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
                        isPreviewingHome = true
                        loadPreview()
                    } else {
                        Toast.makeText(this, "Network Error >_<", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }
    
    private fun saveTagsToPrefs() {
        val set = tagsMap.map { "${it.key}|${it.value}" }.toSet()
        getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE).edit().putStringSet("SAVED_TAGS_V2", set).apply()
    }
    
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
                Toast.makeText(this, "Restored! 🔙", Toast.LENGTH_SHORT).show()
            }
        } else Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveCurrentToGallery() {
        val file = File(filesDir, "wallpaper_home.png")
        if (!file.exists()) return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ACG_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ACGWallpaper")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { contentResolver.openOutputStream(it).use { out -> FileInputStream(file).copyTo(out!!) } }
        Toast.makeText(this, "Saved! 📸", Toast.LENGTH_SHORT).show()
    }

    private fun applyCurrentToTarget(flag: Int, isHome: Boolean) {
        val homeFile = File(filesDir, "wallpaper_home.png")
        val lockFile = File(filesDir, "wallpaper_lock.png")
        val sourceFile = if (isHome) homeFile else { if (lockFile.exists()) lockFile else homeFile }
        if (!sourceFile.exists()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                val wm = WallpaperManager.getInstance(this@MainActivity)
                wm.setBitmap(bitmap, null, true, flag)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    private fun showScheduleDialog() {
        AlertDialog.Builder(this).setTitle("Refresh Frequency").setItems(scheduleOptions) { _, which ->
            getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE).edit().putInt("SCHEDULE_INDEX", which).apply()
            binding.tvScheduleInfo.text = "Auto: ${scheduleOptions[which]} ▾"
            if (binding.switchDaily.isChecked) setupPeriodicWork()
        }.show()
    }

    private fun setupPeriodicWork() {
         val prefs = getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)
         val style = prefs.getInt("STYLE", 50)
         val strictTags = tagsMap.filter { it.value }.keys.toTypedArray()
         val softTags = tagsMap.filter { !it.value }.keys.toTypedArray()
         val scheduleIndex = prefs.getInt("SCHEDULE_INDEX", 0)
         val scheduleValues = intArrayOf(-1, 6, 12, 24)
         val inputData = workDataOf("STYLE_VALUE" to style, "STRICT_TAGS" to strictTags, "SOFT_TAGS" to softTags, "HOME_STATE" to homeState, "LOCK_STATE" to lockState)
         val requestBuilder = if (scheduleIndex == 0) PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS).setInitialDelay(1, TimeUnit.HOURS) else PeriodicWorkRequestBuilder<WallpaperWorker>(scheduleValues[scheduleIndex].toLong(), TimeUnit.HOURS)
         WorkManager.getInstance(this).enqueueUniquePeriodicWork("AUTO_JOB", ExistingPeriodicWorkPolicy.UPDATE, requestBuilder.setInputData(inputData).addTag("AUTO_WALLPAPER").build())
    }
    
    private fun cancelPeriodicWork() { WorkManager.getInstance(this).cancelUniqueWork("AUTO_JOB") }
}
