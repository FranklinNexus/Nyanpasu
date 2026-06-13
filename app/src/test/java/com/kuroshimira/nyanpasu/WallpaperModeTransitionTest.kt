package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.ui.WallpaperModeTransition
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperTargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WallpaperModeTransitionTest {

    @Test
    fun onTargetChanged_nullWhenSync() {
        val context = RuntimeEnvironment.getApplication()
        assertNull(WallpaperModeTransition.onTargetChanged(context, 1, 1))
    }

    @Test
    fun onTargetChanged_clearsStaleLockOnDual() {
        val context = RuntimeEnvironment.getApplication()
        WallpaperFiles.homeFile(context).writeBytes(byteArrayOf(5, 5, 5))
        WallpaperFiles.lockFile(context).writeBytes(byteArrayOf(5, 5, 5))

        assertEquals(
            WallpaperModeTransition.DualLockState.StaleLockCleared,
            WallpaperModeTransition.onTargetChanged(context, 1, 2),
        )
        assertTrue(WallpaperTargetMode.isDualMode(1, 2))
        assertFalse(WallpaperFiles.lockFile(context).exists())
    }
}
