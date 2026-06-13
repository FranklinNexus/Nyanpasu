package com.kuroshimira.nyanpasu.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import kotlin.math.max

/** 预览区解码与 PhotoView 可见区域裁剪。 */
object PreviewHelpers {

    private const val MAX_PREVIEW_SIDE_PX = 2048
    private const val MAX_APPLY_SIDE_PX = 4096

    fun decodeForApply(file: File): Bitmap? = decode(file, MAX_APPLY_SIDE_PX)

    fun decode(file: File): Bitmap? = decode(file, MAX_PREVIEW_SIDE_PX)

    fun visibleCropRect(photoView: PhotoView, imageWidth: Int, imageHeight: Int): Rect? {
        if (imageWidth <= 0 || imageHeight <= 0 || photoView.width <= 0 || photoView.height <= 0) {
            return null
        }
        val displayRect = photoView.displayRect ?: return null
        val scale = displayRect.width() / imageWidth.toFloat()
        val viewWidth = photoView.width.toFloat()
        val viewHeight = photoView.height.toFloat()

        var left = -displayRect.left / scale
        var top = -displayRect.top / scale
        var width = viewWidth / scale
        var height = viewHeight / scale

        if (left < 0) left = 0f
        if (top < 0) top = 0f
        if (left + width > imageWidth) width = imageWidth - left
        if (top + height > imageHeight) height = imageHeight - top
        if (width <= 0f || height <= 0f) return null

        return Rect(
            left.toInt(),
            top.toInt(),
            (left + width).toInt(),
            (top + height).toInt(),
        )
    }

    fun cropFromRect(
        source: Bitmap,
        rect: Rect,
        referenceWidth: Int,
        referenceHeight: Int,
    ): Bitmap? {
        if (referenceWidth == source.width && referenceHeight == source.height) {
            return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        }
        val scaleX = source.width.toFloat() / referenceWidth
        val scaleY = source.height.toFloat() / referenceHeight
        val left = (rect.left * scaleX).toInt().coerceIn(0, source.width - 1)
        val top = (rect.top * scaleY).toInt().coerceIn(0, source.height - 1)
        val right = (rect.right * scaleX).toInt().coerceIn(left + 1, source.width)
        val bottom = (rect.bottom * scaleY).toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun decode(file: File, maxSide: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = inSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun inSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) > maxSide) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }
}
