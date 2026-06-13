package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.wallpaper.WallpaperOemCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperOemCompatTest {

    @Test
    fun syncApplyOrder_xiaomiFamily_usesLockHomeLock() {
        assertEquals(
            WallpaperOemCompat.SyncApplyOrder.LOCK_HOME_LOCK,
            WallpaperOemCompat.syncApplyOrderFor("Xiaomi", "Redmi"),
        )
        assertEquals(
            WallpaperOemCompat.SyncApplyOrder.LOCK_HOME_LOCK,
            WallpaperOemCompat.syncApplyOrderFor("HUAWEI", "HONOR"),
        )
        assertEquals(
            WallpaperOemCompat.SyncApplyOrder.LOCK_HOME_LOCK,
            WallpaperOemCompat.syncApplyOrderFor("OPPO", "realme"),
        )
        assertEquals(
            WallpaperOemCompat.SyncApplyOrder.LOCK_HOME_LOCK,
            WallpaperOemCompat.syncApplyOrderFor("vivo", "iqoo"),
        )
    }

    @Test
    fun syncApplyOrder_stockAndroid_usesHomeLockLock() {
        assertEquals(
            WallpaperOemCompat.SyncApplyOrder.HOME_LOCK_LOCK,
            WallpaperOemCompat.syncApplyOrderFor("Google", "google"),
        )
        assertEquals(
            WallpaperOemCompat.SyncApplyOrder.HOME_LOCK_LOCK,
            WallpaperOemCompat.syncApplyOrderFor("samsung", "samsung"),
        )
    }
}
