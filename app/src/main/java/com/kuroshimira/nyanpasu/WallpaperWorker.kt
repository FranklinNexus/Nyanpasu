package com.kuroshimira.nyanpasu



import android.app.WallpaperManager

import android.content.Context

import android.graphics.Bitmap

import android.util.Log

import androidx.work.CoroutineWorker

import androidx.work.WorkerParameters

import coil.ImageLoader

import coil.request.ImageRequest

import coil.request.SuccessResult

import java.io.File

import java.io.FileOutputStream

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.TimeoutCancellationException

import kotlinx.coroutines.delay

import kotlinx.coroutines.withContext

import kotlinx.coroutines.withTimeout



class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) :

    CoroutineWorker(appContext, workerParams) {



    companion object {

        /** 图源多源串行重试无上限时可能卡数分钟；超时会失败并收起主界面加载状态。 */
        private const val SEARCH_TIMEOUT_HOME_MS = 150_000L
        private const val SEARCH_TIMEOUT_LOCK_MS = 90_000L

        private const val DOWNLOAD_RETRY = 3
        private const val DOWNLOAD_RETRY_DELAY_MS = 1_200L

        @Volatile

        private var imageLoaderInstance: ImageLoader? = null



        private fun imageLoader(context: Context): ImageLoader {

            return imageLoaderInstance ?: synchronized(this) {

                imageLoaderInstance ?:
                    NetworkImageLoader.forApp(context.applicationContext).also { imageLoaderInstance = it }

            }

        }

    }



    override suspend fun doWork(): Result {

        return try {

            val styleValue = inputData.getInt("STYLE_VALUE", 50)

            val strictTags = inputData.getStringArray("STRICT_TAGS") ?: emptyArray()

            val softTags = inputData.getStringArray("SOFT_TAGS") ?: emptyArray()

            val homeState = inputData.getInt("HOME_STATE", 1)

            val lockState = inputData.getInt("LOCK_STATE", 0)

            val isUrgent = inputData.getBoolean("IS_URGENT", true)

            val r18Mode = inputData.getInt("R18_MODE", 0)

            val bufferSlot = inputData.getString("BUFFER_SLOT") ?: "a"



            val targetFilename = when {

                isUrgent -> "wallpaper_home.png"

                bufferSlot == "b" -> "wallpaper_buffer_b.png"

                else -> "wallpaper_buffer_a.png"

            }

            val isSyncMode = (lockState == 1) || (lockState == 2 && homeState == 2)

            val prefs = applicationContext.getSharedPreferences("ACG_PREFS", Context.MODE_PRIVATE)

            val lastStored = prefs.getString("LAST_FETCHED_URL", "") ?: ""



            val searchCtx = SetuSearchEngine.Context(

                styleValue = styleValue,

                strictTags = strictTags,

                softTags = softTags,

                lastFetchedUrl = lastStored,

            )



            var homeUrl =
                try {
                    withTimeout(SEARCH_TIMEOUT_HOME_MS) {
                        SetuSearchEngine.search(searchCtx, r18Mode)
                    }
                } catch (_: TimeoutCancellationException) {
                    Log.e(
                        "WallpaperWorker",
                        "Setu search (home) timed out after ${SEARCH_TIMEOUT_HOME_MS}ms",
                    )
                    ""
                }

            if (homeUrl.isEmpty()) {

                Log.e("WallpaperWorker", "Setu search returned no URL")

                return Result.failure()

            }



            prefs.edit().putString("LAST_FETCHED_URL", homeUrl).apply()



            var lockUrl = ""

            if (isUrgent && !isSyncMode && lockState == 2) {

                val lockCtx = SetuSearchEngine.Context(

                    styleValue = styleValue,

                    strictTags = strictTags,

                    softTags = softTags,

                    lastFetchedUrl = homeUrl,

                )

                lockUrl =
                    try {
                        withTimeout(SEARCH_TIMEOUT_LOCK_MS) {
                            SetuSearchEngine.search(lockCtx, r18Mode)
                        }
                    } catch (_: TimeoutCancellationException) {
                        Log.e(
                            "WallpaperWorker",
                            "Setu search (lock) timed out after ${SEARCH_TIMEOUT_LOCK_MS}ms",
                        )
                        ""
                    }

                if (lockUrl == homeUrl && lockUrl.isNotEmpty()) {

                    lockUrl =
                        try {
                            withTimeout(SEARCH_TIMEOUT_LOCK_MS) {
                                SetuSearchEngine.searchDistinct(lockCtx, r18Mode, homeUrl)
                            }
                        } catch (_: TimeoutCancellationException) {
                            Log.e(
                                "WallpaperWorker",
                                "Setu searchDistinct timed out after ${SEARCH_TIMEOUT_LOCK_MS}ms",
                            )
                            ""
                        }

                }

            }



            val loader = imageLoader(applicationContext)



            var wallpaperApplyFailed = false



            if (homeUrl.isNotEmpty()) {

                val bitmap = downloadBitmap(loader, homeUrl)

                if (bitmap != null) {

                    val processed = ImageProcessor.centerCrop(applicationContext, bitmap)

                    saveToInternalSafely(processed, targetFilename)



                    if (isUrgent) {

                        when {

                            isSyncMode && homeState > 0 && lockState > 0 -> {

                                saveToInternalSafely(processed, "wallpaper_lock.png")

                                if (!applyBitmapWallpaper(

                                        processed,

                                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,

                                    )

                                ) {

                                    val okSys =

                                        applyBitmapWallpaper(processed, WallpaperManager.FLAG_SYSTEM)

                                    val okLock =

                                        applyBitmapWallpaper(processed, WallpaperManager.FLAG_LOCK)

                                    if (!okSys || !okLock) wallpaperApplyFailed = true

                                }

                            }

                            homeState > 0 -> {

                                if (!applyBitmapWallpaper(processed, WallpaperManager.FLAG_SYSTEM)) {

                                    wallpaperApplyFailed = true

                                }

                            }

                            isSyncMode && lockState > 0 -> {

                                saveToInternalSafely(processed, "wallpaper_lock.png")

                                if (!applyBitmapWallpaper(processed, WallpaperManager.FLAG_LOCK)) {

                                    wallpaperApplyFailed = true

                                }

                            }

                        }

                    }

                } else {

                    return Result.failure()

                }

            }



            if (isUrgent && !isSyncMode && lockState == 2 && lockUrl.isNotEmpty()) {

                val bitmap = downloadBitmap(loader, lockUrl)

                if (bitmap != null) {

                    val processed = ImageProcessor.centerCrop(applicationContext, bitmap)

                    saveToInternalSafely(processed, "wallpaper_lock.png")

                    if (!applyBitmapWallpaper(processed, WallpaperManager.FLAG_LOCK)) {

                        wallpaperApplyFailed = true

                    }

                }

            }



            if (isUrgent && wallpaperApplyFailed) {

                Log.e("WallpaperWorker", "setBitmap failed (see earlier logs); marking work failed")

                return Result.failure()

            }



            Result.success()

        } catch (e: Exception) {

            e.printStackTrace()

            Result.failure()

        }

    }



    private suspend fun applyBitmapWallpaper(bitmap: Bitmap, which: Int): Boolean {

        val safe = ImageProcessor.forWallpaperManager(bitmap)

        return try {

            withContext(Dispatchers.Main) {

                val wm = WallpaperManager.getInstance(applicationContext)

                wm.setBitmap(safe, null, true, which)

            }

            true

        } catch (e: Exception) {

            Log.e("WallpaperWorker", "setBitmap failed which=$which", e)

            false

        }

    }



    private suspend fun downloadBitmap(loader: ImageLoader, url: String): Bitmap? {

        val shortUrl = if (url.length > 96) url.take(96) + "…" else url

        repeat(DOWNLOAD_RETRY) { attempt ->

            try {

                val request =
                    ImageRequest.Builder(applicationContext).data(url).allowHardware(false).build()

                when (val outcome = loader.execute(request)) {

                    is SuccessResult -> {

                        val bmp = (outcome.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap

                        if (bmp != null) return bmp

                        Log.w(
                            "WallpaperWorker",
                            "download: SuccessResult but no bitmap (attempt ${attempt + 1}) $shortUrl",
                        )
                    }

                    else -> {

                        Log.w(
                            "WallpaperWorker",
                            "download: ${outcome::class.simpleName} (attempt ${attempt + 1}) $shortUrl",
                        )
                    }

                }

            } catch (e: Exception) {

                Log.w(
                    "WallpaperWorker",
                    "download failed (attempt ${attempt + 1}) $shortUrl",
                    e,
                )

            }

            if (attempt < DOWNLOAD_RETRY - 1) delay(DOWNLOAD_RETRY_DELAY_MS)

        }

        Log.e("WallpaperWorker", "download gave up after $DOWNLOAD_RETRY tries: $shortUrl")

        return null

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

