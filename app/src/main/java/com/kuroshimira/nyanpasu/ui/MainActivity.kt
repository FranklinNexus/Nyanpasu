package com.kuroshimira.nyanpasu.ui

import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.network.NetworkStatus
import com.kuroshimira.nyanpasu.schedule.AutoWallpaperScheduler
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperHistory
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import com.kuroshimira.nyanpasu.wallpaper.WallpaperWriteGuard
import com.kuroshimira.nyanpasu.work.WallpaperWorkNames
import android.app.WallpaperManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import kotlinx.coroutines.launch
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var workBindings: MainActivityWorkBindings
    private lateinit var scheduleUi: ScheduleUiController
    private lateinit var wallpaperUi: WallpaperUiController
    private val tagsMap = mutableMapOf<String, Boolean>()

    private val previewState = PreviewController.State(homeState = 1, lockState = 0, isPreviewingHome = true)
    private var r18Mode = 0

    private lateinit var mascot: MascotController
    private lateinit var tagChips: TagChipController
    private lateinit var preview: PreviewController

    private val refreshWork get() = workBindings.refreshWork
    private val prefetchCoordinator get() = workBindings.prefetchCoordinator
    private val workObserver get() = workBindings.workObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WallpaperHistory.ensureLoaded(this)

        scheduleUi =
            ScheduleUiController(
                activity = this,
                binding = binding,
                callbacks =
                    object : ScheduleUiController.Callbacks {
                        override fun homeState(): Int = previewState.homeState
                        override fun lockState(): Int = previewState.lockState
                        override fun onColdStartRefillPrefetch() =
                            prefetchCoordinator.refillEmptySlots(force = true)
                        override fun onPrefsChangedInvalidatePrefetch() {
                            prefetchCoordinator.invalidatePrefetchSlots()
                            prefetchCoordinator.refillEmptySlots(force = true)
                        }
                        override fun onRequestAutoUpdatePermissions() =
                            AppPermissions.requestAutoUpdateIfNeeded(this@MainActivity)
                    },
            )

        mascot = MascotController(this, binding)

        workBindings =
            MainActivityWorkBindings(
                activity = this,
                binding = binding,
                scope = lifecycleScope,
                previewState = previewState,
                preview = { preview },
                scheduleReschedule = { scheduleUi.rescheduleIfEnabled() },
                workInputBuilder = { urgent, slot -> buildWallpaperWorkInput(urgent, slot) },
            )
        workBindings.wire(owner = this)

        preview =
            PreviewController(
                activity = this,
                binding = binding,
                scope = lifecycleScope,
                state = previewState,
                prefetchCoordinator = { workBindings.prefetchCoordinator },
            )
        wallpaperUi =
            WallpaperUiController(
                activity = this,
                binding = binding,
                scope = lifecycleScope,
                previewState = previewState,
                preview = { preview },
                onFirstLaunchComplete = { prefetchCoordinator.ensureInitialWallpaper() },
            )
        tagChips =
            TagChipController(
                activity = this,
                binding = binding,
                tagsMap = tagsMap,
                tagResponses = mascot.tagResponses,
                onSpeak = { mascot.speak(it) },
                onTagsChanged = { scheduleUi.refreshAfterPrefsChanged() },
                bounceAnimate = { wallpaperUi.bounceAnimate(it) },
            )

        preview.setupAspectRatio()

        val prefs = WallpaperPrefs.prefs(this)
        previewState.homeState = prefs.getInt(WallpaperPrefs.KEY_HOME_STATE, 1)
        previewState.lockState = prefs.getInt(WallpaperPrefs.KEY_LOCK_STATE, 0)
        binding.seekBarStyle.progress = prefs.getInt(WallpaperPrefs.KEY_STYLE, WallpaperPrefs.DEFAULT_STYLE)
        binding.switchDaily.isChecked = prefs.getBoolean(WallpaperPrefs.KEY_DAILY_ENABLED, false)
        wallpaperUi.updateKaomoji(binding.seekBarStyle.progress)

        r18Mode = prefs.getInt(WallpaperPrefs.KEY_R18_MODE, 0)
        when (r18Mode) {
            0 -> binding.toggleGroupMode.check(R.id.btnModeSafe)
            1 -> binding.toggleGroupMode.check(R.id.btnModeR18)
            2 -> binding.toggleGroupMode.check(R.id.btnModeMix)
        }

        scheduleUi.updateInfoText(WallpaperPrefs.readScheduleIndex(prefs))
        wallpaperUi.updateToggleButtons()
        tagChips.restoreFromPrefs(prefs)
        prefetchCoordinator.migrateIfNeeded()
        preview.loadPreview()

        bindControls(prefs)
        wallpaperUi.cleanExpiredHistory()
        AppPermissions.requestStorageIfNeeded(this)
        if (binding.switchDaily.isChecked) {
            AppPermissions.requestAutoUpdateIfNeeded(this)
        }
        wallpaperUi.showWelcomeDialogIfNeeded()
        WallpaperWorkNames.cancelLegacyApplyWorks(this)
        scheduleUi.refreshOnColdStart()

        if (!WallpaperPrefs.isFirstLaunch(prefs) && !prefetchCoordinator.hasAppliedWallpaper()) {
            prefetchCoordinator.ensureInitialWallpaper()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            mascot.speak(getString(R.string.mascot_greeting))
            mascot.scheduleRandomSpeech()
        }, 800)
    }

    private fun bindControls(prefs: android.content.SharedPreferences) {
        binding.ivLogo.setOnClickListener {
            wallpaperUi.bounceAnimate(it)
            mascot.onLogoClick()
        }
        binding.ivPreview.setOnClickListener {
            if (!preview.toggleDualPreviewIfPossible()) {
                wallpaperUi.bounceAnimate(binding.previewCard)
            } else {
                binding.ivPreview.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        binding.tvScheduleInfo.setOnClickListener { scheduleUi.showScheduleDialog() }

        binding.seekBarStyle.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    wallpaperUi.updateKaomoji(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    prefs.edit()
                        .putInt(WallpaperPrefs.KEY_STYLE, seekBar?.progress ?: WallpaperPrefs.DEFAULT_STYLE)
                        .apply()
                    scheduleUi.refreshAfterPrefsChanged()
                }
            },
        )

        binding.etTagInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val text = binding.etTagInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    tagChips.addChip(text, false)
                    tagChips.saveToPrefs()
                    binding.etTagInput.text?.clear()
                }
                return@setOnEditorActionListener true
            }
            false
        }

        binding.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (previewState.homeState == 0 && previewState.lockState == 0) {
                    binding.switchDaily.isChecked = false
                    Toast.makeText(this, getString(R.string.toast_auto_need_target), Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean(WallpaperPrefs.KEY_DAILY_ENABLED, true).apply()
                AppPermissions.requestAutoUpdateIfNeeded(this)
                scheduleUi.reportResult(AutoWallpaperScheduler.schedule(this))
            } else {
                prefs.edit().putBoolean(WallpaperPrefs.KEY_DAILY_ENABLED, false).apply()
                AutoWallpaperScheduler.cancelScheduling(this)
            }
        }

        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.toggleGroupMode.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            when (checkedId) {
                R.id.btnModeSafe -> {
                    r18Mode = 0
                    mascot.speak(getString(R.string.mascot_safe_mode))
                }
                R.id.btnModeMix -> {
                    r18Mode = 2
                    mascot.speak(getString(R.string.mascot_mix_mode))
                }
                R.id.btnModeR18 -> {
                    r18Mode = 1
                    mascot.speak(getString(R.string.mascot_nsfw_mode))
                }
            }
            prefs.edit().putInt(WallpaperPrefs.KEY_R18_MODE, r18Mode).apply()
            scheduleUi.refreshAfterPrefsChanged()
        }

        binding.btnUpdate.setOnClickListener {
            wallpaperUi.bounceAnimate(it)
            if (refreshWork.manualRefreshInProgress || refreshWork.isApplyWorkBusy() ||
                WallpaperWriteGuard.isWriteInProgress()
            ) {
                return@setOnClickListener
            }
            val ready = WallpaperFiles.firstReadyPrefetch(this, currentPrefetchFingerprint())
            val canInstantLoad = ready != null && !prefetchCoordinator.requiresDualWallpaperDownload()
            if (canInstantLoad) {
                lifecycleScope.launch {
                    if (workObserver.isApplyWorkBusy() || WallpaperWriteGuard.isWriteInProgress()) {
                        refreshWork.startOneTimeWork(isUrgent = true)
                        return@launch
                    }
                    val applied = prefetchCoordinator.applyBufferToHome(ready!!)
                    if (applied) {
                        Toast.makeText(this@MainActivity, getString(R.string.status_instant_load), Toast.LENGTH_SHORT)
                            .show()
                    }
                    prefetchCoordinator.refillEmptySlots(force = true)
                }
            } else {
                if (!NetworkStatus.isConnected(this)) {
                    Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                refreshWork.startOneTimeWork(isUrgent = true)
            }
        }

        binding.btnToggleHome.setOnClickListener {
            wallpaperUi.bounceAnimate(it)
            previewState.homeState = (previewState.homeState + 1) % 3
            prefs.edit().putInt(WallpaperPrefs.KEY_HOME_STATE, previewState.homeState).apply()
            wallpaperUi.updateToggleButtons()
            scheduleUi.refreshAfterPrefsChanged()
            if (previewState.homeState > 0) preview.applyCurrentVisibleToTarget(WallpaperManager.FLAG_SYSTEM)
        }
        binding.btnToggleLock.setOnClickListener {
            wallpaperUi.bounceAnimate(it)
            previewState.lockState = (previewState.lockState + 1) % 3
            prefs.edit().putInt(WallpaperPrefs.KEY_LOCK_STATE, previewState.lockState).apply()
            wallpaperUi.updateToggleButtons()
            scheduleUi.refreshAfterPrefsChanged()
            if (previewState.lockState > 0) preview.applyCurrentVisibleToTarget(WallpaperManager.FLAG_LOCK)
        }
        binding.btnUndo.setOnClickListener {
            wallpaperUi.bounceAnimate(it)
            wallpaperUi.undoWallpaper()
        }
        binding.btnSave.setOnClickListener {
            wallpaperUi.bounceAnimate(it)
            wallpaperUi.saveCurrentToGallery()
        }
    }

    private fun currentTagArrays(): Pair<Array<String>, Array<String>> {
        val strict = tagsMap.filter { it.value }.keys.toTypedArray()
        val soft = tagsMap.filter { !it.value }.keys.toTypedArray()
        return strict to soft
    }

    private fun currentPrefetchFingerprint(): String {
        val (strict, soft) = currentTagArrays()
        return WallpaperPrefs.prefetchSnapshotFingerprint(
            styleValue = binding.seekBarStyle.progress,
            r18Mode = r18Mode,
            homeState = previewState.homeState,
            lockState = previewState.lockState,
            strictTags = strict,
            softTags = soft,
        )
    }

    private fun buildWallpaperWorkInput(isUrgent: Boolean, prefetchSlot: String): Data {
        val (strictTags, softTags) = currentTagArrays()
        val builder =
            Data.Builder()
                .putInt("STYLE_VALUE", binding.seekBarStyle.progress)
                .putStringArray("STRICT_TAGS", strictTags)
                .putStringArray("SOFT_TAGS", softTags)
                .putInt("HOME_STATE", previewState.homeState)
                .putInt("LOCK_STATE", previewState.lockState)
                .putInt("R18_MODE", r18Mode)
                .putBoolean("IS_URGENT", isUrgent)
                .putString("BUFFER_SLOT", prefetchSlot)
        if (!isUrgent) {
            builder.putString("PREFETCH_FINGERPRINT", currentPrefetchFingerprint())
        }
        return builder.build()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AppPermissions.handleResult(this, requestCode, grantResults)
    }

    override fun onResume() {
        super.onResume()
        if (this::binding.isInitialized && this::workBindings.isInitialized) {
            workObserver.syncOnResume(lifecycleScope)
            prefetchCoordinator.migrateIfNeeded()
            preview.loadPreview()
            val prefs = WallpaperPrefs.prefs(this)
            if (WallpaperPrefs.consumePendingAutoFailure(prefs)) {
                Toast.makeText(this, getString(R.string.error_download_failed), Toast.LENGTH_LONG).show()
            }
            if (!WallpaperPrefs.isFirstLaunch(prefs)) {
                prefetchCoordinator.maybeApplyToPreview()
                prefetchCoordinator.refillEmptySlots()
            }
        }
    }

    override fun onDestroy() {
        if (this::preview.isInitialized) preview.clearPreviewBitmap()
        if (this::mascot.isInitialized) mascot.destroy()
        super.onDestroy()
    }
}
