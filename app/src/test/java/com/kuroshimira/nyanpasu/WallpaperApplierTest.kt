package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperApplierTest {

    @Test
    fun isSyncMode_pinkLock() {
        assertTrue(WallpaperApplier.isSyncMode(homeState = 1, lockState = 1))
    }

    @Test
    fun isSyncMode_independentBothBlue() {
        assertTrue(WallpaperApplier.isSyncMode(homeState = 2, lockState = 2))
    }

    @Test
    fun isSyncMode_notWhenIndependentLockOnly() {
        assertFalse(WallpaperApplier.isSyncMode(homeState = 1, lockState = 2))
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
