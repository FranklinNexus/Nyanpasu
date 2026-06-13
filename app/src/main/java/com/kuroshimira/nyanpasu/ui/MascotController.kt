package com.kuroshimira.nyanpasu.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import kotlin.random.Random

/** 看板娘台词、随机闲聊与 Logo 彩蛋。 */
class MascotController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
) {

    val tagResponses: Map<String, String> =
        activity.resources.getStringArray(R.array.tag_responses)
            .mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                line.substring(0, sep).trim().lowercase() to line.substring(sep + 1).trim()
            }
            .toMap()

    private val mascotQuotes = activity.resources.getStringArray(R.array.mascot_quotes)
    private val speechHandler = Handler(Looper.getMainLooper())
    private val logoResetHandler = Handler(Looper.getMainLooper())
    private var logoClickCount = 0

    private val randomSpeechRunnable = object : Runnable {
        override fun run() {
            if (!activity.isFinishing) {
                if (binding.tvMascotSpeech.alpha == 0f) {
                    showRandomQuote()
                }
                speechHandler.postDelayed(this, Random.nextLong(30_000, 60_000))
            }
        }
    }

    fun scheduleRandomSpeech() {
        speechHandler.removeCallbacks(randomSpeechRunnable)
        speechHandler.postDelayed(randomSpeechRunnable, Random.nextLong(10_000, 30_000))
    }

    fun speak(text: String) {
        binding.tvMascotSpeech.text = text
        speechHandler.removeCallbacksAndMessages(null)
        binding.tvMascotSpeech.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        speechHandler.postDelayed({
            binding.tvMascotSpeech.animate()
                .alpha(0f)
                .setDuration(800)
                .withEndAction { scheduleRandomSpeech() }
                .start()
        }, 4000)
    }

    fun onLogoClick() {
        logoClickCount++
        if (logoClickCount >= 10) {
            showDeveloperDialog()
            logoClickCount = 0
            return
        }
        logoResetHandler.removeCallbacksAndMessages(null)
        logoResetHandler.postDelayed({ logoClickCount = 0 }, 2000)
        showRandomQuote()
    }

    fun destroy() {
        speechHandler.removeCallbacksAndMessages(null)
        logoResetHandler.removeCallbacksAndMessages(null)
    }

    private fun showRandomQuote() {
        speak(mascotQuotes.random())
    }

    private fun showDeveloperDialog() {
        fireConfetti()
        val versionName = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
        AlertDialog.Builder(activity)
            .setTitle(R.string.developer_title)
            .setMessage(activity.getString(R.string.developer_message, versionName))
            .setPositiveButton(R.string.developer_btn_blog) { _, _ ->
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.developer_blog_url))),
                )
            }
            .setNegativeButton(R.string.developer_btn_contact) { _, _ ->
                try {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.developer_contact_url))),
                    )
                } catch (_: Exception) {
                    Toast.makeText(activity, R.string.telegram_not_found, Toast.LENGTH_SHORT).show()
                }
            }
            .setIcon(R.mipmap.ic_launcher)
            .show()
    }

    private fun fireConfetti() {
        val container = binding.confettiContainer
        val colors = listOf(
            Color.parseColor("#FF80AB"),
            Color.parseColor("#64B5F6"),
            Color.parseColor("#FFD54F"),
            Color.parseColor("#81C784"),
            Color.parseColor("#FF8A65"),
        )
        container.post {
            val maxWidth = container.width
            if (maxWidth < 25) return@post
            for (i in 0..50) {
                val confetti = View(activity)
                confetti.setBackgroundColor(colors.random())
                val size = Random.nextInt(10, 25)
                val params = FrameLayout.LayoutParams(size, size)
                params.leftMargin = Random.nextInt(0, maxWidth - size)
                params.topMargin = -size
                container.addView(confetti, params)
                confetti.animate()
                    .translationY((container.height + 200).toFloat())
                    .rotation(Random.nextInt(0, 360).toFloat() * 5)
                    .setDuration(Random.nextLong(1500, 3000))
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { container.removeView(confetti) }
                    .start()
            }
        }
    }
}
