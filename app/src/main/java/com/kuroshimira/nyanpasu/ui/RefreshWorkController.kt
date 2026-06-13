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
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
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
        fun buildWorkInput(isUrgent: Boolean, prefetchSlot: String, complementLockOnly: Boolean): Data
        fun manualFailureMessage(outcome: WallpaperJobOutcome): String
        fun onManualUrgentSuccess()
        fun onManualUrgentFailure(outcome: WallpaperJobOutcome)
        fun onBackgroundUrgentFinished(succeeded: Boolean)
        fun onLockComplementFinished(succeeded: Boolean)
        fun onAutoApplyFinished(succeeded: Boolean)
        fun refillPrefetchSlots()
    }

    var manualRefreshInProgress = false
        private set

    private var lastHandledUrgentWorkId: UUID? = null
    private var lastHandledComplementWorkId: UUID? = null
    private var lastHandledAutoWorkId: UUID? = null
    private var lastUrgentUserInitiated = true

    fun isApplyWorkBusy(): Boolean = workObserver().isApplyWorkBusyCached()

    fun isComplementWorkBusy(): Boolean = workObserver().isComplementWorkBusyCached()

    fun updateBusyState() {
        if (callbacks.isFinishing()) return
        val applyBusy = isApplyWorkBusy()
        if (manualRefreshInProgress) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnUpdate.isEnabled = false
            binding.btnUpdate.text = activity.getString(R.string.status_summoning)
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnUpdate.isEnabled = !applyBusy
            binding.btnUpdate.text = activity.getString(R.string.btn_refresh)
        }
    }

    fun reconcileRefreshButton(infos: List<WorkInfo>) {
        val prefs = WallpaperPrefs.prefs(activity)
        val persistedHandledId =
            WallpaperPrefs.readLastHandledUrgentWorkId(prefs)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val manualInfos = infos.filter { it.tags.contains(WallpaperWorkNames.TAG_MANUAL_REFRESH) }
        if (manualInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }) {
            if (!manualRefreshInProgress) {
                manualRefreshInProgress = true
                binding.progressBar.visibility = View.VISIBLE
                binding.btnUpdate.isEnabled = false
                binding.btnUpdate.text = activity.getString(R.string.status_summoning)
            }
            return
        }
        manualInfos.firstOrNull { info ->
            info.state.isFinished &&
                info.id != lastHandledUrgentWorkId &&
                info.id != persistedHandledId
        }?.let { info ->
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> handleApplyUrgentFinished(true, info)
                WorkInfo.State.FAILED -> handleApplyUrgentFinished(false, info)
                else -> Unit
            }
            return
        }
        if (!manualRefreshInProgress) return
        if (!WallpaperWorkNames.isApplyUrgentActive(infos)) {
            manualRefreshInProgress = false
            resetRefreshButton()
        }
    }

    fun handleApplyUrgentFinished(succeeded: Boolean, info: WorkInfo) {
        if (info.id == lastHandledUrgentWorkId) return
        lastHandledUrgentWorkId = info.id
        WallpaperPrefs.saveLastHandledUrgentWorkId(WallpaperPrefs.prefs(activity), info.id)

        val outcome = WallpaperJobOutcome.fromWorkData(info.outputData)
        val isManualJob = info.tags.contains(WallpaperWorkNames.TAG_MANUAL_REFRESH)
        if (isManualJob) {
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
            return
        }
        callbacks.onBackgroundUrgentFinished(succeeded)
        if (succeeded) callbacks.refillPrefetchSlots()
    }

    fun handleComplementFinished(succeeded: Boolean, info: WorkInfo) {
        if (info.id == lastHandledComplementWorkId) return
        lastHandledComplementWorkId = info.id
        WallpaperPrefs.saveLastHandledComplementWorkId(WallpaperPrefs.prefs(activity), info.id)
        callbacks.onLockComplementFinished(succeeded)
        if (succeeded) callbacks.refillPrefetchSlots()
    }

    fun handleAutoApplyFinished(succeeded: Boolean, info: WorkInfo) {
        if (info.id == lastHandledAutoWorkId) return
        lastHandledAutoWorkId = info.id
        callbacks.onAutoApplyFinished(succeeded)
        if (succeeded) callbacks.refillPrefetchSlots()
    }

    fun reconcileComplementWork(infos: List<WorkInfo>) {
        val prefs = WallpaperPrefs.prefs(activity)
        val persistedHandledId =
            WallpaperPrefs.readLastHandledComplementWorkId(prefs)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        infos.firstOrNull { info ->
            info.state.isFinished &&
                info.id != lastHandledComplementWorkId &&
                info.id != persistedHandledId
        }?.let { info ->
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> handleComplementFinished(true, info)
                WorkInfo.State.FAILED -> handleComplementFinished(false, info)
                else -> Unit
            }
        }
    }

    /** 用户点 Refresh：取消 complement / 后台 urgent，强制入队手动任务。 */
    fun startManualRefresh() {
        if (manualRefreshInProgress) return
        if (WallpaperWriteGuard.isWriteInProgress()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.status_cooling_down),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val wm = WorkManager.getInstance(activity)
        wm.cancelUniqueWork(WallpaperWorkNames.APPLY_DUAL_COMPLEMENT)
        lastHandledUrgentWorkId = null
        lastUrgentUserInitiated = true
        manualRefreshInProgress = true
        binding.progressBar.visibility = View.VISIBLE
        binding.btnUpdate.isEnabled = false
        binding.btnUpdate.text = activity.getString(R.string.status_summoning)
        wm.enqueueUniqueWork(
            WallpaperWorkNames.APPLY_URGENT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(
                    callbacks.buildWorkInput(
                        isUrgent = true,
                        prefetchSlot = "a",
                        complementLockOnly = false,
                    ),
                )
                .addTag(WallpaperWorkNames.TAG_MANUAL_REFRESH)
                .build(),
        )
    }

    fun startOneTimeWork(isUrgent: Boolean, prefetchSlot: String = "a", userInitiated: Boolean = true) {
        val wm = WorkManager.getInstance(activity)
        if (isUrgent) {
            if (userInitiated) {
                startManualRefresh()
                return
            }
            lastHandledUrgentWorkId = null
            lastUrgentUserInitiated = false
            wm.enqueueUniqueWork(
                WallpaperWorkNames.APPLY_URGENT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(
                        callbacks.buildWorkInput(
                            isUrgent = true,
                            prefetchSlot = prefetchSlot,
                            complementLockOnly = false,
                        ),
                    )
                    .build(),
            )
        } else {
            wm.enqueueUniqueWork(
                WallpaperWorkNames.prefetchWorkName(prefetchSlot),
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(callbacks.buildWorkInput(isUrgent = false, prefetchSlot = prefetchSlot, complementLockOnly = false))
                    .build(),
            )
        }
    }

    fun startLockComplementWork() {
        if (WallpaperWriteGuard.isWriteInProgress()) return
        lastHandledComplementWorkId = null
        WorkManager.getInstance(activity).enqueueUniqueWork(
            WallpaperWorkNames.APPLY_DUAL_COMPLEMENT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(
                    callbacks.buildWorkInput(
                        isUrgent = false,
                        prefetchSlot = "a",
                        complementLockOnly = true,
                    ),
                )
                .addTag(WallpaperWorkNames.TAG_DUAL_COMPLEMENT)
                .build(),
        )
    }

    private fun resetRefreshButton() {
        binding.progressBar.visibility = View.GONE
        binding.btnUpdate.isEnabled = true
        binding.btnUpdate.text = activity.getString(R.string.btn_refresh)
    }
}
