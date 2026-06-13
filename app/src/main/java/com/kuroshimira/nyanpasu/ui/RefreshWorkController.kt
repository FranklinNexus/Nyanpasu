package com.kuroshimira.nyanpasu.ui

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import com.kuroshimira.nyanpasu.wallpaper.WallpaperWriteGuard
import com.kuroshimira.nyanpasu.work.WallpaperJobOutcome
import com.kuroshimira.nyanpasu.work.WallpaperWorkNames
import com.kuroshimira.nyanpasu.work.WallpaperWorkObserver
import com.kuroshimira.nyanpasu.work.WallpaperWorker
import java.util.UUID

/** 手动 Refresh / urgent 队列的按钮状态与 WorkManager 入队。 */
class RefreshWorkController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val workObserver: () -> WallpaperWorkObserver,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun isFinishing(): Boolean
        fun buildWorkInput(isUrgent: Boolean, prefetchSlot: String): Data
        fun manualFailureMessage(outcome: WallpaperJobOutcome): String
        fun onManualUrgentSuccess()
        fun onManualUrgentFailure(outcome: WallpaperJobOutcome)
        fun onAutoApplyFinished(succeeded: Boolean)
        fun refillPrefetchSlots()
    }

    var manualRefreshInProgress = false
        private set

    private var lastHandledUrgentWorkId: UUID? = null
    private var lastHandledAutoWorkId: UUID? = null

    fun isApplyWorkBusy(): Boolean = workObserver().isApplyWorkBusyCached()

    fun updateBusyState() {
        if (callbacks.isFinishing()) return
        val busy = workObserver().isApplyWorkBusyCached() || WallpaperWriteGuard.isWriteInProgress()
        if (busy) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnUpdate.isEnabled = false
            if (manualRefreshInProgress) {
                binding.btnUpdate.text = activity.getString(R.string.status_summoning)
            }
        } else if (!manualRefreshInProgress) {
            resetRefreshButton()
        }
    }

    fun reconcileRefreshButton(infos: List<WorkInfo>) {
        if (!manualRefreshInProgress) return
        if (WallpaperWorkNames.isManualApplyActive(infos)) return
        val manualInfos = infos.filter { it.tags.contains(WallpaperWorkNames.TAG_MANUAL_REFRESH) }
        manualInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }?.let {
            handleApplyUrgentFinished(true, it)
            return
        }
        manualInfos.firstOrNull { it.state == WorkInfo.State.FAILED }?.let {
            handleApplyUrgentFinished(false, it)
        }
    }

    fun handleApplyUrgentFinished(succeeded: Boolean, info: WorkInfo) {
        if (info.id == lastHandledUrgentWorkId) return
        lastHandledUrgentWorkId = info.id
        if (!info.tags.contains(WallpaperWorkNames.TAG_MANUAL_REFRESH)) return

        val outcome = WallpaperJobOutcome.fromWorkData(info.outputData)
        manualRefreshInProgress = false
        resetRefreshButton()
        if (succeeded) {
            callbacks.onManualUrgentSuccess()
            callbacks.refillPrefetchSlots()
        } else {
            Toast.makeText(activity, callbacks.manualFailureMessage(outcome), Toast.LENGTH_LONG).show()
            callbacks.onManualUrgentFailure(outcome)
            if (outcome.applyResult.homeOk) callbacks.refillPrefetchSlots()
        }
    }

    fun handleAutoApplyFinished(succeeded: Boolean, info: WorkInfo) {
        if (info.id == lastHandledAutoWorkId) return
        lastHandledAutoWorkId = info.id
        callbacks.onAutoApplyFinished(succeeded)
        if (succeeded) callbacks.refillPrefetchSlots()
    }

    fun startOneTimeWork(isUrgent: Boolean, prefetchSlot: String = "a") {
        val wm = WorkManager.getInstance(activity)
        if (isUrgent) {
            lastHandledUrgentWorkId = null
            manualRefreshInProgress = true
            binding.progressBar.visibility = View.VISIBLE
            binding.btnUpdate.isEnabled = false
            binding.btnUpdate.text = activity.getString(R.string.status_summoning)
            wm.enqueueUniqueWork(
                WallpaperWorkNames.APPLY_URGENT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(callbacks.buildWorkInput(isUrgent = true, prefetchSlot = prefetchSlot))
                    .addTag(WallpaperWorkNames.TAG_MANUAL_REFRESH)
                    .build(),
            )
        } else {
            wm.enqueueUniqueWork(
                WallpaperWorkNames.prefetchWorkName(prefetchSlot),
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(callbacks.buildWorkInput(isUrgent = false, prefetchSlot = prefetchSlot))
                    .build(),
            )
        }
    }

    private fun resetRefreshButton() {
        binding.progressBar.visibility = View.GONE
        binding.btnUpdate.isEnabled = true
        binding.btnUpdate.text = activity.getString(R.string.btn_refresh)
    }
}
