package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplier
import com.kuroshimira.nyanpasu.wallpaper.WallpaperTargetMode
import com.kuroshimira.nyanpasu.work.WallpaperPipeline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperTargetModeTest {

    @Test
    fun sync_pinkPink() {
        assertTrue(WallpaperTargetMode.isSyncMode(1, 1))
        assertFalse(WallpaperTargetMode.isDualMode(1, 1))
    }

    @Test
    fun sync_blueBlue() {
        assertTrue(WallpaperTargetMode.isSyncMode(2, 2))
        assertFalse(WallpaperTargetMode.isDualMode(2, 2))
    }

    @Test
    fun dual_pinkBlue() {
        assertTrue(WallpaperTargetMode.isDualMode(1, 2))
        assertFalse(WallpaperTargetMode.isSyncMode(1, 2))
        assertTrue(WallpaperTargetMode.needsTwoImages(1, 2))
    }

    @Test
    fun dual_bluePink_sameAsPinkBlue() {
        assertTrue(WallpaperTargetMode.isDualMode(2, 1))
        assertTrue(WallpaperTargetMode.isDualMode(1, 2))
        assertFalse(WallpaperTargetMode.isSyncMode(2, 1))
    }

    @Test
    fun singleTarget_notDualOrSync() {
        assertFalse(WallpaperTargetMode.isDualMode(1, 0))
        assertFalse(WallpaperTargetMode.isSyncMode(1, 0))
        assertFalse(WallpaperTargetMode.isDualMode(0, 2))
    }
}

class WallpaperApplierTest {

    @Test
    fun isSyncMode_delegatesToTargetMode() {
        assertTrue(WallpaperApplier.isSyncMode(homeState = 1, lockState = 1))
        assertTrue(WallpaperApplier.isSyncMode(homeState = 2, lockState = 2))
        assertFalse(WallpaperApplier.isSyncMode(homeState = 1, lockState = 2))
        assertFalse(WallpaperApplier.isSyncMode(homeState = 2, lockState = 1))
    }

    @Test
    fun applyResult_fullySucceeded() {
        val r = WallpaperApplyResult(homeOk = true, lockOk = true)
        assertTrue(r.fullySucceeded)
    }

    @Test
    fun applyResult_lockPartialFailure() {
        val r = WallpaperApplyResult(homeOk = true, lockOk = false)
        assertFalse(r.fullySucceeded)
        assertTrue(r.lockFailed)
    }
}

class WallpaperPipelineTest {

    @Test
    fun needsDualSearch_onlyUrgentDual() {
        assertTrue(WallpaperPipeline.needsDualSearch(isUrgent = true, homeState = 1, lockState = 2))
        assertTrue(WallpaperPipeline.needsDualSearch(isUrgent = true, homeState = 2, lockState = 1))
        assertFalse(WallpaperPipeline.needsDualSearch(isUrgent = false, homeState = 1, lockState = 2))
        assertFalse(WallpaperPipeline.needsDualSearch(isUrgent = true, homeState = 1, lockState = 1))
    }

    @Test
    fun evaluateJobOk_dualLockFailure() {
        assertFalse(
            WallpaperPipeline.evaluateJobOk(
                homeState = 1,
                lockState = 2,
                homeRequired = true,
                applyResult = WallpaperApplyResult(homeOk = true, lockOk = false),
                lockSearchFailed = false,
                lockDownloadFailed = true,
            ),
        )
    }

    @Test
    fun evaluateJobOk_syncBothOk() {
        assertTrue(
            WallpaperPipeline.evaluateJobOk(
                homeState = 1,
                lockState = 1,
                homeRequired = true,
                applyResult = WallpaperApplyResult(homeOk = true, lockOk = true),
                lockSearchFailed = false,
                lockDownloadFailed = false,
            ),
        )
    }
}
