package com.kuroshimira.nyanpasu.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import com.kuroshimira.nyanpasu.work.PrefetchCoordinator
import com.kuroshimira.nyanpasu.work.WallpaperJobOutcome
import com.kuroshimira.nyanpasu.work.WallpaperWorkNames
import com.kuroshimira.nyanpasu.work.WallpaperWorkObserver
import kotlinx.coroutines.CoroutineScope

/** MainActivity 的 WorkManager / 预取 / Refresh  wiring，减轻 Activity 体积。 */
internal class MainActivityWorkBindings(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val scope: CoroutineScope,
    private val previewState: PreviewController.State,
    private val preview: () -> PreviewController,
    private val scheduleReschedule: () -> Unit,
    private val workInputBuilder: (isUrgent: Boolean, prefetchSlot: String) -> Data,
) {
    lateinit var refreshWork: RefreshWorkController
        private set
    lateinit var prefetchCoordinator: PrefetchCoordinator
        private set
    lateinit var workObserver: WallpaperWorkObserver
        private set

    fun wire(owner: LifecycleOwner) {
        refreshWork =
            RefreshWorkController(
                activity = activity,
                binding = binding,
                workObserver = { workObserver },
                callbacks =
                    object : RefreshWorkController.Callbacks {
                        override fun isFinishing(): Boolean = activity.isFinishing

                        override fun buildWorkInput(isUrgent: Boolean, prefetchSlot: String) =
                            workInputBuilder(isUrgent, prefetchSlot)

                        override fun manualFailureMessage(outcome: WallpaperJobOutcome): String =
                            when {
                                outcome.isPreApplyFailure() ->
                                    activity.getString(R.string.toast_download_failed)
                                outcome.applyResult.homeOk &&
                                    (outcome.lockSearchFailed || outcome.lockDownloadFailed ||
                                        outcome.applyResult.lockFailed) ->
                                    activity.getString(R.string.error_apply_lock_failed)
                                outcome.applyResult.homeOk ->
                                    activity.getString(R.string.error_apply_generic)
                                else ->
                                    activity.getString(R.string.toast_download_failed)
                            }

                        override fun onManualUrgentSuccess() {
                            previewState.isPreviewingHome = true
                            preview().loadPreview()
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.toast_wallpaper_applied),
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (binding.switchDaily.isChecked) {
                                scheduleReschedule()
                            }
                        }

                        override fun onManualUrgentFailure(outcome: WallpaperJobOutcome) {
                            if (outcome.applyResult.homeOk) {
                                previewState.isPreviewingHome = true
                                preview().loadPreview()
                            }
                        }

                        override fun onAutoApplyFinished(succeeded: Boolean) {
                            if (succeeded) {
                                previewState.isPreviewingHome = true
                                preview().loadPreview()
                            }
                        }

                        override fun refillPrefetchSlots() =
                            prefetchCoordinator.refillEmptySlots(force = true)
                    },
            )

        prefetchCoordinator =
            PrefetchCoordinator(
                context = activity,
                scope = scope,
                callbacks =
                    object : PrefetchCoordinator.Callbacks {
                        override fun enqueuePrefetch(slot: String) =
                            refreshWork.startOneTimeWork(isUrgent = false, prefetchSlot = slot)

                        override fun enqueueUrgentDownload() =
                            refreshWork.startOneTimeWork(isUrgent = true)

                        override fun reloadPreview() {
                            previewState.isPreviewingHome = true
                            preview().loadPreview()
                        }

                        override fun onWallpaperApplyFailed(message: String) {
                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                        }
                    },
            )

        workObserver =
            WallpaperWorkObserver(
                owner = owner,
                workManager = WorkManager.getInstance(activity),
                callbacks =
                    object : WallpaperWorkObserver.Callbacks {
                        override fun isFinishing(): Boolean = activity.isFinishing
                        override fun onPeriodicWorkSucceeded() = preview().loadPreview()
                        override fun onPrefetchWorkSucceeded() = prefetchCoordinator.maybeApplyToPreview()
                        override fun onApplyUrgentInfosUpdated(infos: List<WorkInfo>) {
                            refreshWork.updateBusyState()
                            if (!WallpaperWorkNames.isApplyUrgentActive(infos)) {
                                refreshWork.reconcileRefreshButton(infos)
                            }
                        }
                        override fun onAutoApplyInfosUpdated(infos: List<WorkInfo>) {
                            refreshWork.updateBusyState()
                        }
                        override fun onPeriodicInfosUpdated(infos: List<WorkInfo>) {
                            refreshWork.updateBusyState()
                        }
                        override fun onApplyUrgentFinished(succeeded: Boolean, info: WorkInfo) =
                            refreshWork.handleApplyUrgentFinished(succeeded, info)
                        override fun onAutoApplyFinished(succeeded: Boolean, info: WorkInfo) =
                            refreshWork.handleAutoApplyFinished(succeeded, info)
                    },
            )
        workObserver.register()
    }
}
