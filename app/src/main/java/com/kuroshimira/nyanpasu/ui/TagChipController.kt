package com.kuroshimira.nyanpasu.ui

import android.content.SharedPreferences
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs

/** Tag Chip 增删改与 SharedPreferences 同步。 */
class TagChipController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val tagsMap: MutableMap<String, Boolean>,
    private val tagResponses: Map<String, String>,
    private val onSpeak: (String) -> Unit,
    private val onTagsChanged: () -> Unit,
    private val bounceAnimate: (View) -> Unit,
) {

    fun restoreFromPrefs(prefs: SharedPreferences) {
        val savedTagsSet = prefs.getStringSet(WallpaperPrefs.KEY_SAVED_TAGS, emptySet()) ?: emptySet()
        savedTagsSet.forEach { entry ->
            WallpaperPrefs.parseTagEntry(entry)?.let { (name, isStrict) ->
                addChip(name, isStrict)
            } ?: addChip(entry, false)
        }
    }

    fun addChip(tagText: String, isStrict: Boolean) {
        if (tagsMap.containsKey(tagText)) return
        val lowerTag = tagText.lowercase().trim().replace(" ", "_")
        tagResponses[lowerTag]?.let { onSpeak(it) }
        tagsMap[tagText] = isStrict
        val chip = Chip(activity)
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
            saveToPrefs()
            Toast.makeText(
                activity,
                if (newState) R.string.toast_strict_mode else R.string.toast_soft_mode,
                Toast.LENGTH_SHORT,
            ).show()
        }
        chip.setOnCloseIconClickListener {
            binding.chipGroupTags.removeView(chip)
            tagsMap.remove(tagText)
            saveToPrefs()
        }
        binding.chipGroupTags.addView(chip)
    }

    fun saveToPrefs() {
        val set = tagsMap.map { WallpaperPrefs.formatTagEntry(it.key, it.value) }.toSet()
        WallpaperPrefs.prefs(activity).edit().putStringSet(WallpaperPrefs.KEY_SAVED_TAGS, set).apply()
        onTagsChanged()
    }

    private fun updateChipStyle(chip: Chip, isStrict: Boolean) {
        if (isStrict) {
            chip.chipBackgroundColor = ColorStateList.valueOf(activity.getColor(R.color.brand_pink))
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
}
