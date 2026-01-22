package com.kuroshimira.nyanpasu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * 🖼️ 图片处理工具类
 * 轻量级图片适配，用于将任意尺寸的图片适配到设备屏幕
 */
object ImageProcessor {

    /**
     * 📱 使用 Center-Crop 技术将图片适配到屏幕尺寸
     *
     * Center-Crop 原理：
     * 1. 计算缩放比例，确保图片完全填充屏幕
     * 2. 从中心裁剪多余部分
     * 3. 保持图片不变形
     *
     * @param context Android Context
     * @param originalBitmap 原始图片
     * @return 适配后的 Bitmap
     */
    fun centerCrop(context: Context, originalBitmap: Bitmap): Bitmap {
        // 1. 获取屏幕尺寸
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // 2. 计算原始图片和屏幕的宽高比
        val bitmapRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()

        // 3. 计算缩放比例和偏移量
        val scale: Float
        var dx = 0f
        var dy = 0f

        if (bitmapRatio > screenRatio) {
            // 图片比屏幕更宽 -> 按高度缩放，裁剪左右两边
            scale = screenHeight.toFloat() / originalBitmap.height.toFloat()
            dx = (screenWidth - originalBitmap.width * scale) * 0.5f
        } else {
            // 图片比屏幕更长（或一样） -> 按宽度缩放，裁剪上下两边
            scale = screenWidth.toFloat() / originalBitmap.width.toFloat()
            dy = (screenHeight - originalBitmap.height * scale) * 0.5f
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
        canvas.drawBitmap(originalBitmap, matrix, paint)

        return targetBitmap
    }
}
