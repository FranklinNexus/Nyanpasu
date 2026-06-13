package com.kuroshimira.nyanpasu.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
/**
 * 🖼️ 图片处理工具类
 * 轻量级图片适配，用于将任意尺寸的图片适配到设备屏幕
 */
object ImageProcessor {

    private const val MIN_DOWNLOAD_MAX_PX = 2048
    private const val ABSOLUTE_DOWNLOAD_MAX_PX = 4096

    /** Coil 解码与兜底缩放的上限：最长边 ≈ 2× 屏幕，夹在 2048–4096。 */
    fun maxDownloadDimension(context: Context): Int {
        val dm = context.resources.displayMetrics
        val longest = maxOf(dm.widthPixels, dm.heightPixels)
        return (longest * 2).coerceIn(MIN_DOWNLOAD_MAX_PX, ABSOLUTE_DOWNLOAD_MAX_PX)
    }

    /** 解码后若仍超大（如 Coil 未生效），按比例缩小以免 centerCrop OOM。 */
    fun downscaleIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true).also { scaled ->
            if (scaled !== source && !source.isRecycled) source.recycle()
        }
    }

    /**
     * Center-Crop 将图片适配到屏幕尺寸。
     * @param recycleSource 仅当调用方拥有 bitmap（如 Coil 下载）时设为 true
     */
    fun centerCrop(context: Context, originalBitmap: Bitmap, recycleSource: Boolean = false): Bitmap {
        val maxDim = maxDownloadDimension(context)
        val bitmap = downscaleIfNeeded(originalBitmap, maxDim)
        val ownsBitmap = bitmap !== originalBitmap
        // 1. 获取屏幕尺寸
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // 2. 计算原始图片和屏幕的宽高比
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()

        // 3. 计算缩放比例和偏移量
        val scale: Float
        var dx = 0f
        var dy = 0f

        if (bitmapRatio > screenRatio) {
            // 图片比屏幕更宽 -> 按高度缩放，裁剪左右两边
            scale = screenHeight.toFloat() / bitmap.height.toFloat()
            dx = (screenWidth - bitmap.width * scale) * 0.5f
        } else {
            // 图片比屏幕更长（或一样） -> 按宽度缩放，裁剪上下两边
            scale = screenWidth.toFloat() / bitmap.width.toFloat()
            dy = (screenHeight - bitmap.height * scale) * 0.5f
        }

        // 4. 创建矩阵进行变换
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx + 0.5f, dy + 0.5f)

        // 5. 使用 Canvas 绘制新图片（更精确的方法）
        val targetBitmap = Bitmap.createBitmap(
            screenWidth,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        if ((recycleSource || ownsBitmap) && targetBitmap !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return targetBitmap
    }

    /**
     * OEM / 系统对 [WallpaperManager.setBitmap] 偶发只接受软件 ARGB；解码或 Coil 偶发 HARDWARE。
     * [Bitmap.Config.HARDWARE] 仅 API 26+ 存在，minSdk 24 时需在分支内引用以满足 Lint。
     */
    fun forWallpaperManager(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return bitmap
        if (bitmap.config != Bitmap.Config.HARDWARE) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, true).also {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
