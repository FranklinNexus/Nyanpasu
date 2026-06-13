package com.kuroshimira.nyanpasu.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object WallpaperFiles {

    const val HOME = "wallpaper_home.png"
    const val LOCK = "wallpaper_lock.png"
    const val BUFFER_A = "wallpaper_buffer_a.png"
    const val BUFFER_B = "wallpaper_buffer_b.png"
    const val LEGACY_BUFFER = "wallpaper_buffer.png"

    fun homeFile(context: Context): File = File(context.filesDir, HOME)
    fun lockFile(context: Context): File = File(context.filesDir, LOCK)
    fun bufferFile(context: Context, slot: String): File =
        File(context.filesDir, if (slot == "b") BUFFER_B else BUFFER_A)

    fun hasHomeWallpaper(context: Context): Boolean {
        val f = homeFile(context)
        return f.exists() && f.length() > 0L
    }

    fun firstReadyPrefetch(context: Context, expectedFingerprint: String? = null): File? {
        migrateLegacyBuffer(context)
        val prefs = WallpaperPrefs.prefs(context)
        val ready = listOf(
            File(context.filesDir, BUFFER_A),
            File(context.filesDir, BUFFER_B),
        ).filter { it.exists() && it.length() > 0L }
        val matched =
            if (expectedFingerprint == null) {
                ready
            } else {
                ready.filter { file ->
                    val slot = if (file.name == BUFFER_B) "b" else "a"
                    WallpaperPrefs.readBufferFingerprint(prefs, slot) == expectedFingerprint
                }
            }
        return matched.maxByOrNull { it.lastModified() }
    }

    fun migrateLegacyBuffer(context: Context) {
        val leg = File(context.filesDir, LEGACY_BUFFER)
        if (!leg.exists() || leg.length() == 0L) return
        val a = File(context.filesDir, BUFFER_A)
        if (!a.exists() || a.length() == 0L) {
            leg.copyTo(a, overwrite = true)
        }
        leg.delete()
    }

    /**
     * 校验预取文件可解码后，原子提升到主屏文件；成功才删除 [prefetch]。
     * @return false 表示预取损坏或 IO 失败，[prefetch] 保留
     */
    fun promotePrefetchToHome(context: Context, prefetch: File): Boolean {
        if (!prefetch.exists() || prefetch.length() == 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(prefetch.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val homeFile = homeFile(context)
        val tempFile = File(context.filesDir, "$HOME.${System.nanoTime()}.tmp")
        return try {
            prefetch.copyTo(tempFile, overwrite = true)
            if (!tempFile.renameTo(homeFile)) {
                tempFile.copyTo(homeFile, overwrite = true)
                tempFile.delete()
            }
            prefetch.delete()
            WallpaperHistory.backup(context)
            true
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
    }

    fun clearPrefetchBuffers(context: Context) {
        bufferFile(context, "a").delete()
        bufferFile(context, "b").delete()
        File(context.filesDir, LEGACY_BUFFER).delete()
    }

    fun saveBitmapSafely(context: Context, bitmap: Bitmap, filename: String) {
        val tempFile = File(context.filesDir, "$filename.${System.nanoTime()}.tmp")
        val finalFile = File(context.filesDir, filename)
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
            tempFile.delete()
            throw e
        }
    }
}
