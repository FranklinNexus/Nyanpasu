package com.kuroshimira.nyanpasu

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random
import kotlinx.coroutines.delay

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val BASE_URL = "https://api.lolicon.app/setu/v2"

    override suspend fun doWork(): Result {
        return try {
            val styleValue = inputData.getInt("STYLE_VALUE", 50)
            val strictTags = inputData.getStringArray("STRICT_TAGS") ?: emptyArray()
            val softTags = inputData.getStringArray("SOFT_TAGS") ?: emptyArray()
            val homeState = inputData.getInt("HOME_STATE", 1)
            val lockState = inputData.getInt("LOCK_STATE", 0)
            val isUrgent = inputData.getBoolean("IS_URGENT", true)
            val r18Mode = inputData.getInt("R18_MODE", 2) // 0=Pure, 1=NSFW, 2=Mix
            
            val targetFilename = if (isUrgent) "wallpaper_home.png" else "wallpaper_buffer.png"
            val isSyncMode = (lockState == 1) || (lockState == 2 && homeState == 2)
            val prefs = applicationContext.getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)

            // --- 🎯 V27.0 渐进式智能搜索 ---
            var homeUrl = executeProgressiveSearch(prefs, styleValue, strictTags, softTags, r18Mode)
            
            if (homeUrl.isEmpty()) {
                Log.e("Worker", "All search strategies exhausted.")
                return Result.failure()
            }

            prefs.edit().putString("LAST_FETCHED_URL", homeUrl).apply()

            // 独立锁屏逻辑
            var lockUrl = ""
            if (!isSyncMode && lockState == 2) {
                lockUrl = executeProgressiveSearch(prefs, styleValue, strictTags, softTags, r18Mode)
                if (lockUrl == homeUrl && lockUrl.isNotEmpty()) {
                    Thread.sleep(200)
                    lockUrl = executeProgressiveSearch(prefs, styleValue, strictTags, softTags, r18Mode)
                }
            }

            // --- 下载与应用 ---
            val wm = WallpaperManager.getInstance(applicationContext)
            val loader = ImageLoader(applicationContext)

            if (homeUrl.isNotEmpty()) {
                val bitmap = downloadBitmap(loader, homeUrl)
                if (bitmap != null) {
                    val processed = ImageProcessor.centerCrop(applicationContext, bitmap)
                    saveToInternalSafely(processed, targetFilename)
                    
                    if (isUrgent) {
                        if (homeState > 0) wm.setBitmap(processed, null, true, WallpaperManager.FLAG_SYSTEM)
                        if (isSyncMode) {
                            wm.setBitmap(processed, null, true, WallpaperManager.FLAG_LOCK)
                            saveToInternalSafely(processed, "wallpaper_lock.png")
                        }
                    }
                } else {
                    return Result.failure()
                }
            }

            if (!isSyncMode && lockState == 2 && lockUrl.isNotEmpty()) {
                val bitmap = downloadBitmap(loader, lockUrl)
                if (bitmap != null) {
                    val processed = ImageProcessor.centerCrop(applicationContext, bitmap)
                    wm.setBitmap(processed, null, true, WallpaperManager.FLAG_LOCK)
                    saveToInternalSafely(processed, "wallpaper_lock.png")
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    // --- 🧠 V27.0 渐进式搜索算法 (Progressive Search) ---
    private fun executeProgressiveSearch(
        prefs: SharedPreferences,
        styleValue: Int,
        strictTags: Array<String>,
        softTags: Array<String>,
        r18Mode: Int
    ): String {
        val lastUrl = prefs.getString("LAST_FETCHED_URL", "") ?: ""
        
        // 如果没有用户Tag，直接走随机模式
        if (strictTags.isEmpty()) {
            return searchRandom(styleValue, r18Mode, lastUrl)
        }

        // 1️⃣ 【第一阶段】精准搜索：用户Tag + 用户选择的R18模式
        Log.d("Worker", "🎯 Phase 1: Precise search with user tags + r18Mode=$r18Mode")
        val tagVariants = TagExpander.expand(strictTags)
        val sliderEnabled = !TagExpander.isConflictWithSlider(strictTags)
        
        for ((index, variantTag) in tagVariants.withIndex()) {
            Log.d("Worker", "  Trying variant ${index + 1}/${tagVariants.size}: $variantTag")
            
            // 尝试多次（最多3次），应对重复URL
            for (attempt in 1..3) {
                // 带 Slider
                if (sliderEnabled) {
                    val url = buildAndFetch(variantTag, softTags, styleValue, r18Mode, useSlider = true)
                    if (isValidAndNew(url, lastUrl)) {
                        Log.d("Worker", "✅ Found (variant $variantTag + slider, attempt $attempt)")
                        return url
                    }
                }
                
                // 不带 Slider
                val url = buildAndFetch(variantTag, softTags, styleValue, r18Mode, useSlider = false)
                if (isValidAndNew(url, lastUrl)) {
                    Log.d("Worker", "✅ Found (variant $variantTag, attempt $attempt)")
                    return url
                }
                
                // 如果遇到重复，短暂延迟后重试
                if (attempt < 3) Thread.sleep(100)
            }
        }

        // 2️⃣ 【第二阶段】放宽R18限制：保留用户Tag，但尝试其他R18模式
        Log.d("Worker", "🔓 Phase 2: Relaxed R18 mode (keep user tags)")
        val relaxedModes = when (r18Mode) {
            0 -> listOf(2, 1)     // Pure -> 先试Mix，再试NSFW
            1 -> listOf(2, 0)     // NSFW -> 先试Mix，再试Pure
            2 -> listOf(1, 0)     // Mix -> 先试NSFW，再试Pure
            else -> listOf(2)
        }
        
        for (relaxedMode in relaxedModes) {
            for (variantTag in tagVariants) {
                for (attempt in 1..2) {  // 每个模式尝试2次
                    if (sliderEnabled) {
                        val url = buildAndFetch(variantTag, softTags, styleValue, relaxedMode, useSlider = true)
                        if (isValidAndNew(url, lastUrl)) {
                            Log.d("Worker", "✅ Found with relaxed mode r18=$relaxedMode")
                            return url
                        }
                    }
                    
                    val url = buildAndFetch(variantTag, softTags, styleValue, relaxedMode, useSlider = false)
                    if (isValidAndNew(url, lastUrl)) {
                        Log.d("Worker", "✅ Found with relaxed mode r18=$relaxedMode")
                        return url
                    }
                }
            }
        }

        // 3️⃣ 【第三阶段】部分降级：放弃用户Tag，但保留风格偏好（Slider）
        Log.d("Worker", "⚠️ Phase 3: Partial fallback (drop tags, keep slider)")
        val sliderOnlyUrl = searchWithSliderOnly(styleValue, r18Mode, lastUrl)
        if (sliderOnlyUrl.isNotEmpty()) return sliderOnlyUrl

        // 4️⃣ 【第四阶段】完全随机（最后的保底）
        Log.w("Worker", "🎲 Phase 4: Full random fallback")
        return searchRandom(styleValue, r18Mode, lastUrl)
    }

    // 辅助方法：只用 Slider 搜索
    private fun searchWithSliderOnly(styleValue: Int, r18Mode: Int, lastUrl: String): String {
        for (attempt in 1..3) {
            val url = buildAndFetch(emptyList(), emptyArray(), styleValue, r18Mode, useSlider = true)
            if (isValidAndNew(url, lastUrl)) return url
        }
        return ""
    }

    // 辅助方法：完全随机
    private fun searchRandom(styleValue: Int, r18Mode: Int, lastUrl: String): String {
        // 尝试所有R18模式，确保有结果
        val modes = listOf(r18Mode, 2, 1, 0)  // 优先用户选择，然后Mix, NSFW, Pure
        for (mode in modes) {
            for (attempt in 1..2) {
                val url = fetchInternal(BASE_URL + "?r18=$mode&size=regular")
                if (url.isNotEmpty()) return url  // 随机模式不检查重复，保证有结果
            }
        }
        return ""
    }

    // 构建URL并请求
    private fun buildAndFetch(
        primaryTags: List<String>,
        softTags: Array<String>,
        styleValue: Int,
        r18Mode: Int,
        useSlider: Boolean
    ): String {
        val params = StringBuilder("?r18=$r18Mode&size=regular")
        
        // 核心Tag
        primaryTags.forEach { params.append("&tag=${encode(it)}") }
        
        // Slider Tag
        if (useSlider) {
            when (styleValue) {
                in 0..30 -> {
                    params.append("&tag=${encode("loli")}")
                    if (primaryTags.isEmpty() && Random.nextBoolean()) {
                        params.append("&tag=${encode("flat_chest")}")
                    }
                }
                in 70..100 -> {
                    params.append("&tag=${encode("huge_breasts")}")
                    if (primaryTags.isEmpty() && Random.nextBoolean()) {
                        params.append("&tag=${encode("mature_female")}")
                    }
                }
            }
        }
        
        // 软标签（降低概率，避免污染精准搜索）
        if (primaryTags.isEmpty()) {
            softTags.forEach {
                if (Random.nextInt(100) < 25) params.append("&tag=${encode(it)}")
            }
        }

        return fetchInternal(BASE_URL + params.toString())
    }

    private fun isValidAndNew(url: String, lastUrl: String): Boolean {
        return url.isNotEmpty() && url != lastUrl
    }

    // --- 📖 智能 Tag 词典 ---
    object TagExpander {
        fun expand(userTags: Array<String>): List<List<String>> {
            if (userTags.isEmpty()) return listOf(emptyList())

            val result = mutableListOf<List<String>>()
            
            // 检测特殊关键词
            val hasOtokonoko = userTags.any { it.lowercase() in listOf("男娘", "伪娘", "femboy", "trap", "otokonoko", "shota") }
            val hasTouhou = userTags.any { it.lowercase() in listOf("东方", "touhou", "东方project") }
            val hasGenshin = userTags.any { it.lowercase() in listOf("原神", "genshin", "genshin impact") }
            val hasBlueArchive = userTags.any { it.lowercase() in listOf("蔚蓝档案", "ba", "blue archive") }

            if (hasOtokonoko) {
                // ✨ 伪娘系扩展（优化顺序：从最精准到最宽泛）
                result.add(listOf("otokonoko"))           // 最精准
                result.add(listOf("femboy"))              // 英文常用
                result.add(listOf("otoko_no_ko"))         // 日文罗马音
                result.add(listOf("crossdressing"))       // 女装
                result.add(listOf("trap"))                // 老标签
                result.add(listOf("josou_seme"))          // 女装攻
                result.add(listOf("josou_uke"))           // 女装受
                result.add(listOf("male", "1boy"))        // 最宽泛：男性角色
            } else if (hasTouhou) {
                result.add(listOf("Touhou"))
                result.add(listOf("touhou_project"))
                result.add(listOf("東方"))
            } else if (hasGenshin) {
                result.add(listOf("Genshin_Impact"))
                result.add(listOf("genshin"))
                result.add(listOf("原神"))
            } else if (hasBlueArchive) {
                result.add(listOf("Blue_Archive"))
                result.add(listOf("ブルーアーカイブ"))
            } else {
                // 普通Tag，直接映射
                result.add(userTags.map { mapSingle(it) })
            }
            
            return result
        }

        private fun mapSingle(input: String): String {
            val lower = input.trim().lowercase()
            return when (lower) {
                "蔚蓝档案", "ba" -> "Blue_Archive"
                "原神", "genshin" -> "Genshin_Impact"
                "东方", "touhou" -> "Touhou"
                "白毛", "白发", "white hair" -> "white_hair"
                "黑丝", "black pantyhose" -> "black_pantyhose"
                "兽耳", "猫耳", "nekomimi" -> "animal_ears"
                else -> input
            }
        }
        
        fun isConflictWithSlider(userTags: Array<String>): Boolean {
            val conflictKeywords = listOf(
                "男娘", "伪娘", "femboy", "otokonoko", "trap", "crossdressing",
                "shota", "boy", "male", "1boy", "males_only", "male_focus"
            )
            return userTags.any { tag ->
                conflictKeywords.any { kw -> tag.lowercase().contains(kw) }
            }
        }
    }

    // --- 基础工具 ---
    private fun encode(str: String): String = try { URLEncoder.encode(str, "UTF-8") } catch (e: Exception) { str }
    
    private fun fetchInternal(urlString: String): String {
        try {
            val urlObj = URL(urlString)
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if(conn.responseCode != 200) return ""
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close()
            val jsonObject = JSONObject(response.toString())
            val dataArray = jsonObject.optJSONArray("data")
            if (dataArray != null && dataArray.length() > 0) {
                val index = if (dataArray.length() > 1) Random.nextInt(dataArray.length()) else 0
                return dataArray.getJSONObject(index).getJSONObject("urls").getString("regular")
            }
        } catch (e: Exception) {
            Log.e("Fetch", "Error: ${e.message}")
        }
        return ""
    }
    
    private suspend fun downloadBitmap(loader: ImageLoader, url: String): Bitmap? {
        return try {
            val request = ImageRequest.Builder(applicationContext).data(url).allowHardware(false).build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
        } catch (e: Exception) { null }
    }

    private fun saveToInternalSafely(bitmap: Bitmap, filename: String) {
        val tempFile = File(applicationContext.filesDir, "$filename.tmp")
        val finalFile = File(applicationContext.filesDir, filename)
        try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
        }
    }
}
