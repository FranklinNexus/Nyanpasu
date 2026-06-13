package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WallpaperFilesTest {

    @Test
    fun dropLockIfSameAsHome_removesIdenticalLock() {
        val context = RuntimeEnvironment.getApplication()
        val home = WallpaperFiles.homeFile(context)
        val lock = WallpaperFiles.lockFile(context)
        home.writeBytes(byteArrayOf(1, 2, 3, 4))
        lock.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertTrue(WallpaperFiles.dropLockIfSameAsHome(context))
        assertFalse(lock.exists())
        assertTrue(home.exists())
    }

    @Test
    fun isDualWallpaperComplete_requiresDistinctFiles() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs.prefs(context)
        prefs.edit()
            .putInt(com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs.KEY_HOME_STATE, 1)
            .putInt(com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs.KEY_LOCK_STATE, 2)
            .apply()
        WallpaperFiles.homeFile(context).writeBytes(byteArrayOf(1, 2, 3))
        WallpaperFiles.lockFile(context).writeBytes(byteArrayOf(1, 2, 3))
        assertFalse(WallpaperFiles.isDualWallpaperComplete(context))
        WallpaperFiles.lockFile(context).writeBytes(byteArrayOf(9, 8, 7))
        assertTrue(WallpaperFiles.isDualWallpaperComplete(context))
    }
}
