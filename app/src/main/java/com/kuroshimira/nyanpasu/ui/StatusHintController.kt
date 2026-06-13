package com.kuroshimira.nyanpasu.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding

/** 底部 Refresh 上方的内联提示，避免 Toast 遮挡按钮。 */
class StatusHintController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
) {

    fun show(message: String) {
        if (activity.isFinishing) return
        binding.tvStatusHint.text = message
        binding.tvStatusHint.visibility = View.VISIBLE
    }

    fun clear() {
        if (activity.isFinishing) return
        binding.tvStatusHint.visibility = View.GONE
    }
}
