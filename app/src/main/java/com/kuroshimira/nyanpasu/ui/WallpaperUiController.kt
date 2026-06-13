package com.kuroshimira.nyanpasu.ui

import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperHistory
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/** 主屏/锁屏切换 UI、撤销、保存相册、首次引导。 */
class WallpaperUiController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val scope: CoroutineScope,
    private val previewState: PreviewController.State,
    private val preview: () -> PreviewController,
    private val onFirstLaunchComplete: () -> Unit,
) {

    private var undoInProgress = false

    fun bounceAnimate(view: View) {
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    fun updateKaomoji(progress: Int) {
        binding.tvStyleDesc.text =
            when (progress) {
                in 0..20 -> "( ˶˘ ³˘)🍬"
                in 21..40 -> "(｡•́‿•́｡)✨"
                in 80..100 -> "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)💋"
                in 60..79 -> "(¬‿¬)🍷"
                else -> "(・_・)🎲"
            }
    }

    fun updateToggleButtons() {
        val pink = ColorStateList.valueOf(activity.getColor(R.color.brand_pink))
        val blue = ColorStateList.valueOf(activity.getColor(R.color.brand_blue))
        val gray = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        when (previewState.homeState) {
            1 -> setupButton(binding.btnToggleHome, pink, R.drawable.ic_check_bold, Color.WHITE)
            2 -> setupButton(binding.btnToggleHome, blue, R.drawable.ic_check_bold, Color.WHITE)
            else -> setupButton(binding.btnToggleHome, gray, android.R.drawable.checkbox_off_background, Color.GRAY)
        }
        when (previewState.lockState) {
            1 -> setupButton(binding.btnToggleLock, pink, R.drawable.ic_check_bold, Color.WHITE)
            2 -> setupButton(binding.btnToggleLock, blue, R.drawable.ic_check_bold, Color.WHITE)
            else -> setupButton(binding.btnToggleLock, gray, android.R.drawable.checkbox_off_background, Color.GRAY)
        }
        preview().loadPreview()
    }

    fun undoWallpaper() {
        if (undoInProgress) return
        undoInProgress = true
        scope.launch {
            try {
                when (WallpaperHistory.undo(activity)) {
                    WallpaperHistory.UndoResult.Restored -> {
                        preview().loadPreview()
                        toast(R.string.wallpaper_restored, Toast.LENGTH_SHORT)
                    }
                    WallpaperHistory.UndoResult.FileMissing ->
                        toast(R.string.toast_history_missing, Toast.LENGTH_SHORT)
                    WallpaperHistory.UndoResult.ApplyFailed -> {
                        preview().loadPreview()
                        toast(R.string.toast_undo_apply_failed, Toast.LENGTH_LONG)
                    }
                    WallpaperHistory.UndoResult.Empty ->
                        toast(R.string.toast_history_empty, Toast.LENGTH_SHORT)
                }
            } finally {
                undoInProgress = false
            }
        }
    }

    fun saveCurrentToGallery() {
        val lockFile = WallpaperFiles.lockFile(activity)
        val file =
            when {
                previewState.isPreviewingHome -> WallpaperFiles.homeFile(activity)
                lockFile.exists() && lockFile.length() > 0L -> lockFile
                else -> WallpaperFiles.homeFile(activity)
            }
        if (!file.exists()) {
            toast(R.string.toast_nothing_to_save, Toast.LENGTH_SHORT)
            return
        }
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    try {
                        val values =
                            ContentValues().apply {
                                put(
                                    MediaStore.Images.Media.DISPLAY_NAME,
                                    "Nyanpasu_${System.currentTimeMillis()}.png",
                                )
                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                put(
                                    MediaStore.Images.Media.RELATIVE_PATH,
                                    Environment.DIRECTORY_PICTURES + "/Nyanpasu",
                                )
                            }
                        val uri =
                            activity.contentResolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values,
                            )
                        if (uri == null) {
                            SaveResult.PermissionDenied
                        } else {
                            activity.contentResolver.openOutputStream(uri).use { out ->
                                FileInputStream(file).copyTo(out!!)
                            }
                            SaveResult.Ok
                        }
                    } catch (_: Exception) {
                        SaveResult.Failed
                    }
                }
            when (result) {
                SaveResult.Ok -> toast(R.string.wallpaper_saved, Toast.LENGTH_SHORT)
                SaveResult.PermissionDenied -> toast(R.string.toast_save_permission, Toast.LENGTH_LONG)
                SaveResult.Failed -> toast(R.string.toast_save_failed, Toast.LENGTH_LONG)
            }
        }
    }

    fun showWelcomeDialogIfNeeded() {
        val prefs = WallpaperPrefs.prefs(activity)
        if (!WallpaperPrefs.isFirstLaunch(prefs)) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_message)
            .setPositiveButton(R.string.welcome_btn_start) { _, _ ->
                WallpaperPrefs.markFirstLaunchComplete(prefs)
                onFirstLaunchComplete()
            }
            .setCancelable(false)
            .setIcon(R.mipmap.ic_launcher)
            .show()
    }

    fun cleanExpiredHistory() {
        scope.launch(Dispatchers.IO) {
            WallpaperHistory.cleanExpired(activity)
        }
    }

    private enum class SaveResult { Ok, PermissionDenied, Failed }

    private fun setupButton(button: MaterialButton, tint: ColorStateList, icon: Int, textColor: Int) {
        button.backgroundTintList = tint
        button.setTextColor(textColor)
        button.iconTint = ColorStateList.valueOf(textColor)
        button.setIconResource(icon)
    }

    private fun toast(resId: Int, duration: Int) {
        Toast.makeText(activity, activity.getString(resId), duration).show()
    }
}
