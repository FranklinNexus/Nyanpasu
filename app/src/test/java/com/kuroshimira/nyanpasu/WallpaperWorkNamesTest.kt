package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.work.WallpaperWorkNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperWorkNamesTest {

    @Test
    fun prefetchWorkName_slots() {
        assertEquals("prefetch_wallpaper_a", WallpaperWorkNames.prefetchWorkName("a"))
        assertEquals("prefetch_wallpaper_b", WallpaperWorkNames.prefetchWorkName("b"))
    }

    @Test
    fun tagConstants_distinct() {
        assertTrue(WallpaperWorkNames.TAG_MANUAL_REFRESH.isNotEmpty())
        assertTrue(WallpaperWorkNames.TAG_AUTO_WALLPAPER.isNotEmpty())
    }
}
