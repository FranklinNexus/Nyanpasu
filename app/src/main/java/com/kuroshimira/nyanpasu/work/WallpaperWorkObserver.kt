package com.kuroshimira.nyanpasu.work
import com.kuroshimira.nyanpasu.schedule.AutoWallpaperScheduler

import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 集中注册 WorkManager LiveData，跟踪 urgent / periodic / prefetch 状态转换。
 */
class WallpaperWorkObserver(
    private val owner: LifecycleOwner,
    private val workManager: WorkManager,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun isFinishing(): Boolean
        fun onPeriodicWorkSucceeded()
        fun onPrefetchWorkSucceeded()
        fun onApplyUrgentInfosUpdated(infos: List<WorkInfo>)
        fun onAutoApplyInfosUpdated(infos: List<WorkInfo>)
        fun onComplementInfosUpdated(infos: List<WorkInfo>)
        fun onPeriodicInfosUpdated(infos: List<WorkInfo>)
        fun onApplyUrgentFinished(succeeded: Boolean, info: WorkInfo)
        fun onAutoApplyFinished(succeeded: Boolean, info: WorkInfo)
        fun onComplementFinished(succeeded: Boolean, info: WorkInfo)
    }

    private val lastWorkStates = mutableMapOf<String, WorkInfo.State>()

    var lastApplyUrgentInfos: List<WorkInfo> = emptyList()
        private set

    var lastPeriodicInfos: List<WorkInfo> = emptyList()
        private set

    var lastAutoApplyInfos: List<WorkInfo> = emptyList()
        private set

    var lastComplementInfos: List<WorkInfo> = emptyList()
        private set

    fun register() {
        observeAutoWorkCompletion(AutoWallpaperScheduler.WORK_PERIODIC)
        observePrefetchWorkCompletion()
        observeApplyWork(WallpaperWorkNames.APPLY_URGENT, manualOnly = true)
        observeApplyWork(WallpaperWorkNames.APPLY_AUTO, manualOnly = false)
        observeComplementWork()
        observePeriodicApplyBusy()
    }

    suspend fun syncOnResume() {
        val wm = workManager
        val applyInfos =
            withContext(Dispatchers.Default) {
                wm.getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_URGENT).get()
            }
        val autoInfos =
            withContext(Dispatchers.Default) {
                wm.getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_AUTO).get()
            }
        val complementInfos =
            withContext(Dispatchers.Default) {
                wm.getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_DUAL_COMPLEMENT).get()
            }
        val periodicInfos =
            withContext(Dispatchers.Default) {
                wm.getWorkInfosForUniqueWork(AutoWallpaperScheduler.WORK_PERIODIC).get()
            }
        withContext(Dispatchers.Main) {
            if (callbacks.isFinishing()) return@withContext
            lastApplyUrgentInfos = applyInfos
            lastAutoApplyInfos = autoInfos
            lastComplementInfos = complementInfos
            lastPeriodicInfos = periodicInfos
            callbacks.onApplyUrgentInfosUpdated(applyInfos)
            callbacks.onAutoApplyInfosUpdated(autoInfos)
            callbacks.onComplementInfosUpdated(complementInfos)
            callbacks.onPeriodicInfosUpdated(periodicInfos)
        }
    }

    suspend fun isApplyWorkBusy(): Boolean {
        val applyInfos =
            withContext(Dispatchers.Default) {
                workManager.getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_URGENT).get()
            }
        val autoInfos =
            withContext(Dispatchers.Default) {
                workManager.getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_AUTO).get()
            }
        val periodicInfos =
            withContext(Dispatchers.Default) {
                workManager.getWorkInfosForUniqueWork(AutoWallpaperScheduler.WORK_PERIODIC).get()
            }
        return WallpaperWorkNames.isApplyUrgentActive(applyInfos) ||
            WallpaperWorkNames.isApplyUrgentActive(autoInfos) ||
            WallpaperWorkNames.isPeriodicApplyRunning(periodicInfos)
    }

    fun isApplyWorkBusyCached(): Boolean =
        WallpaperWorkNames.isApplyUrgentActive(lastApplyUrgentInfos) ||
            WallpaperWorkNames.isApplyUrgentActive(lastAutoApplyInfos) ||
            WallpaperWorkNames.isPeriodicApplyRunning(lastPeriodicInfos)

    fun isComplementWorkBusyCached(): Boolean =
        WallpaperWorkNames.isApplyUrgentActive(lastComplementInfos)

    private fun observeComplementWork() {
        workManager.getWorkInfosForUniqueWorkLiveData(WallpaperWorkNames.APPLY_DUAL_COMPLEMENT).observe(owner) { infos ->
            if (callbacks.isFinishing()) return@observe
            lastComplementInfos = infos
            callbacks.onComplementInfosUpdated(infos)
            infos.forEach { info ->
                val key = "${WallpaperWorkNames.APPLY_DUAL_COMPLEMENT}:${info.id}"
                val prev = trackWorkState(key, info.state)
                if (prev != null && !prev.isFinished && info.state.isFinished) {
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> callbacks.onComplementFinished(true, info)
                        WorkInfo.State.FAILED -> callbacks.onComplementFinished(false, info)
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeAutoWorkCompletion(uniqueWorkName: String) {
        workManager.getWorkInfosForUniqueWorkLiveData(uniqueWorkName).observe(owner) { infos ->
            if (callbacks.isFinishing()) return@observe
            infos.forEach { info ->
                if (onWorkTransitionToSuccess("$uniqueWorkName:${info.id}", info.state)) {
                    callbacks.onPeriodicWorkSucceeded()
                }
            }
        }
    }

    private fun observePrefetchWorkCompletion() {
        listOf("a", "b").forEach { slot ->
            val name = WallpaperWorkNames.prefetchWorkName(slot)
            workManager.getWorkInfosForUniqueWorkLiveData(name).observe(owner) { infos ->
                if (callbacks.isFinishing()) return@observe
                infos.forEach { info ->
                    if (onWorkTransitionToSuccess("$name:${info.id}", info.state)) {
                        callbacks.onPrefetchWorkSucceeded()
                    }
                }
            }
        }
    }

    private fun observeApplyWork(uniqueWorkName: String, manualOnly: Boolean) {
        workManager.getWorkInfosForUniqueWorkLiveData(uniqueWorkName).observe(owner) { infos ->
            if (callbacks.isFinishing()) return@observe
            if (manualOnly) {
                lastApplyUrgentInfos = infos
                callbacks.onApplyUrgentInfosUpdated(infos)
            } else {
                lastAutoApplyInfos = infos
                callbacks.onAutoApplyInfosUpdated(infos)
            }
            infos.forEach { info ->
                val key = "$uniqueWorkName:${info.id}"
                val prev = trackWorkState(key, info.state)
                if (prev != null && !prev.isFinished && info.state.isFinished) {
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED ->
                            if (manualOnly) {
                                callbacks.onApplyUrgentFinished(true, info)
                            } else {
                                callbacks.onAutoApplyFinished(true, info)
                            }
                        WorkInfo.State.FAILED ->
                            if (manualOnly) {
                                callbacks.onApplyUrgentFinished(false, info)
                            } else {
                                callbacks.onAutoApplyFinished(false, info)
                            }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observePeriodicApplyBusy() {
        workManager.getWorkInfosForUniqueWorkLiveData(AutoWallpaperScheduler.WORK_PERIODIC).observe(owner) { infos ->
            if (callbacks.isFinishing()) return@observe
            lastPeriodicInfos = infos
            callbacks.onPeriodicInfosUpdated(infos)
        }
    }

    private fun trackWorkState(key: String, state: WorkInfo.State): WorkInfo.State? {
        val prev = lastWorkStates[key]
        if (state.isFinished) {
            lastWorkStates.remove(key)
        } else {
            lastWorkStates[key] = state
        }
        return prev
    }

    /** 仅 RUNNING→SUCCEEDED 时返回 true，忽略冷启动时的历史成功记录。 */
    private fun onWorkTransitionToSuccess(key: String, state: WorkInfo.State): Boolean {
        val prev = trackWorkState(key, state)
        return prev != null && !prev.isFinished && state == WorkInfo.State.SUCCEEDED
    }
}
