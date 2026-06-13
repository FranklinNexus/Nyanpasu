package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.wallpaper.WallpaperHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperHistoryTest {

    @Test
    fun undoResult_hasExpectedStates() {
        val names = WallpaperHistory.UndoResult.entries.map { it.name }
        assertTrue(names.contains("Restored"))
        assertTrue(names.contains("Empty"))
        assertEquals(4, WallpaperHistory.UndoResult.entries.size)
    }
}
