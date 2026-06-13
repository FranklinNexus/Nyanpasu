package com.kuroshimira.nyanpasu.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.View
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplier
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperWriteGuard
import com.kuroshimira.nyanpasu.work.PrefetchCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 预览框比例、双屏切换指示、解码显示与可见区域裁剪设壁纸。
 */
class PreviewController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val scope: CoroutineScope,
    private val state: State,
    private val prefetchCoordinator: PrefetchCoordinator,
) {

    data class State(
        var homeState: Int,
        var lockState: Int,
        var isPreviewingHome: Boolean,
    )

    private var previewLoadGeneration = 0
    private var previewSourceKey: String? = null

    fun setupAspectRatio() {
        val metrics = activity.resources.displayMetrics
        val ratioString = "${metrics.widthPixels}:${metrics.heightPixels}"
        val params = binding.previewCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.dimensionRatio = ratioString
        binding.previewCard.layoutParams = params
    }

    fun loadPreview() {
        updateIndicator()
        val homeFile = WallpaperFiles.homeFile(activity)
        val lockFile = WallpaperFiles.lockFile(activity)
        val targetFile = if (state.isPreviewingHome) homeFile else lockFile
        val finalFile = if (targetFile.exists()) targetFile else if (homeFile.exists()) homeFile else null

        val generation = ++previewLoadGeneration
        if (finalFile == null) {
            previewSourceKey = null
            clearPreviewBitmap()
            return
        }

        val sourceKey = "${finalFile.absolutePath}:${finalFile.lastModified()}:${state.isPreviewingHome}"
        if (sourceKey != previewSourceKey) {
            binding.ivPreview.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            previewSourceKey = sourceKey
        }

        scope.launch {
            val file = finalFile
            val bitmap = withContext(Dispatchers.IO) { PreviewHelpers.decode(file) }
            if (generation != previewLoadGeneration || activity.isFinishing) {
                bitmap?.recycle()
                return@launch
            }
            if (bitmap != null) {
                setPreviewBitmap(bitmap)
            } else {
                Log.e("PreviewController", "Failed to decode ${file.name}, removing corrupt file")
                clearPreviewBitmap()
                WallpaperWriteGuard.withWriteLock {
                    withContext(Dispatchers.IO) { file.delete() }
                }
                prefetchCoordinator.maybeApplyToPreview()
            }
        }
    }

    fun toggleDualPreviewIfPossible(): Boolean {
        val homeFile = WallpaperFiles.homeFile(activity)
        val lockFile = WallpaperFiles.lockFile(activity)
        val isDualMode = (state.homeState != state.lockState) && (state.homeState > 0 && state.lockState > 0)
        if (!isDualMode || !homeFile.exists() || !lockFile.exists()) return false
        state.isPreviewingHome = !state.isPreviewingHome
        binding.ivPreview.animate().alpha(0.5f).setDuration(100).withEndAction {
            loadPreview()
            binding.ivPreview.animate().alpha(1f).setDuration(100).start()
        }.start()
        return true
    }

    fun applyCurrentVisibleToTarget(flag: Int) {
        val photoView = binding.ivPreview
        val previewBitmap = (photoView.drawable as? BitmapDrawable)?.bitmap ?: return
        val sourceFile = wallpaperFileForFlag(flag)
        if (!sourceFile.exists()) {
            scope.launch {
                WallpaperWriteGuard.withWriteLock { applyFromFileLocked(flag) }
            }
            return
        }
        val refW = previewBitmap.width
        val refH = previewBitmap.height
        val cropRect = PreviewHelpers.visibleCropRect(photoView, refW, refH)
        if (cropRect == null) {
            scope.launch {
                WallpaperWriteGuard.withWriteLock { applyFromFileLocked(flag) }
            }
            return
        }
        scope.launch {
            WallpaperWriteGuard.withWriteLock {
                try {
                    var sourceBitmap: Bitmap? = null
                    val croppedBitmap = withContext(Dispatchers.IO) {
                        sourceBitmap = PreviewHelpers.decodeForApply(sourceFile) ?: return@withContext null
                        PreviewHelpers.cropFromRect(sourceBitmap!!, cropRect, refW, refH)
                    }
                    sourceBitmap?.takeIf { !it.isRecycled }?.recycle()
                    if (croppedBitmap == null) {
                        applyFromFileLocked(flag)
                        return@withWriteLock
                    }
                    val displayMetrics = activity.resources.displayMetrics
                    val finalBitmap = withContext(Dispatchers.Default) {
                        Bitmap.createScaledBitmap(
                            croppedBitmap,
                            displayMetrics.widthPixels,
                            displayMetrics.heightPixels,
                            true,
                        ).also { scaled ->
                            if (scaled !== croppedBitmap) croppedBitmap.recycle()
                        }
                    }
                    try {
                        val result = applyBitmapForToggle(finalBitmap, flag)
                        showToggleResult(flag, result)
                    } finally {
                        if (!finalBitmap.isRecycled) finalBitmap.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    applyFromFileLocked(flag)
                }
            }
        }
    }

    fun clearPreviewBitmap() {
        val old = (binding.ivPreview.drawable as? BitmapDrawable)?.bitmap
        binding.ivPreview.setImageDrawable(null)
        if (old != null && !old.isRecycled) old.recycle()
    }

    private fun updateIndicator() {
        val isDualMode =
            (state.homeState != state.lockState) && (state.homeState > 0 && state.lockState > 0)
        if (!isDualMode) {
            binding.tvViewIndicator.visibility = View.GONE
            return
        }
        binding.tvViewIndicator.visibility = View.VISIBLE
        val pinkColor = ColorStateList.valueOf(activity.getColor(R.color.brand_pink))
        val blueColor = ColorStateList.valueOf(activity.getColor(R.color.brand_blue))
        if (state.isPreviewingHome) {
            binding.tvViewIndicator.text = activity.getString(R.string.indicator_home)
            binding.tvViewIndicator.backgroundTintList = if (state.homeState == 2) blueColor else pinkColor
        } else {
            binding.tvViewIndicator.text = activity.getString(R.string.indicator_lock)
            binding.tvViewIndicator.backgroundTintList = if (state.lockState == 2) blueColor else pinkColor
        }
    }

    private fun wallpaperFileForFlag(flag: Int): File {
        val homeFile = WallpaperFiles.homeFile(activity)
        val lockFile = WallpaperFiles.lockFile(activity)
        return if (flag == WallpaperManager.FLAG_SYSTEM) {
            homeFile
        } else if (lockFile.exists() && lockFile.length() > 0L) {
            lockFile
        } else {
            homeFile
        }
    }

    private suspend fun applyFromFileLocked(flag: Int) {
        val homeFile = WallpaperFiles.homeFile(activity)
        val lockFile = WallpaperFiles.lockFile(activity)
        val sourceFile = if (flag == WallpaperManager.FLAG_SYSTEM) homeFile else lockFile
        val finalFile = if (sourceFile.exists()) sourceFile else homeFile
        if (!finalFile.exists()) return
        var bitmap: Bitmap? = null
        try {
            bitmap = withContext(Dispatchers.IO) { PreviewHelpers.decode(finalFile) } ?: return
            val result = applyBitmapForToggle(bitmap, flag)
            showToggleResult(flag, result)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    /** 与 Worker 一致：sync 模式双写锁屏，避免只写单 flag 导致 OEM 锁屏重置。 */
    private suspend fun applyBitmapForToggle(bitmap: Bitmap, flag: Int): WallpaperApplyResult {
        if (activity.isFinishing) {
            return WallpaperApplyResult(homeOk = false, lockOk = false)
        }
        val sync = WallpaperApplier.isSyncMode(state.homeState, state.lockState)
        val lockBitmap =
            if (!sync && state.lockState == 2 && flag == WallpaperManager.FLAG_LOCK) {
                bitmap
            } else {
                null
            }
        return WallpaperApplier.applyForStates(
            context = activity,
            homeBitmap = bitmap,
            homeState = state.homeState,
            lockState = state.lockState,
            lockBitmap = lockBitmap,
        )
    }

    private suspend fun showToggleResult(flag: Int, result: WallpaperApplyResult) {
        withContext(Dispatchers.Main) {
            if (activity.isFinishing) return@withContext
            val target =
                if (flag == WallpaperManager.FLAG_SYSTEM) {
                    activity.getString(R.string.target_home)
                } else {
                    activity.getString(R.string.target_lock)
                }
            val ok =
                if (WallpaperApplier.isSyncMode(state.homeState, state.lockState)) {
                    result.fullySucceeded
                } else if (flag == WallpaperManager.FLAG_SYSTEM) {
                    result.homeOk
                } else {
                    result.lockOk
                }
            if (ok) {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_target_updated, target),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_target_apply_failed, target),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun setPreviewBitmap(bitmap: Bitmap) {
        val old = (binding.ivPreview.drawable as? BitmapDrawable)?.bitmap
        binding.ivPreview.setImageBitmap(bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
    }
}
