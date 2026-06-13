package com.kuroshimira.nyanpasu

import android.content.SharedPreferences
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPrefsTest {

    @Test
    fun coerceScheduleIndex_clampsOutOfRange() {
        assertEquals(0, WallpaperPrefs.coerceScheduleIndex(-1))
        assertEquals(3, WallpaperPrefs.coerceScheduleIndex(99))
    }

    @Test
    fun readScheduleIndex_clampsInvalidPrefs() {
        val prefs = fakePrefs(WallpaperPrefs.KEY_SCHEDULE_INDEX to 42)
        assertEquals(3, WallpaperPrefs.readScheduleIndex(prefs))
    }

    @Test
    fun readTags_splitsStrictAndSoft() {
        val prefs = fakePrefs(
            WallpaperPrefs.KEY_SAVED_TAGS to setOf("cat|true", "dog|false", "legacy-only")
        )
        val (strict, soft) = WallpaperPrefs.readTags(prefs)
        assertArrayEquals(arrayOf("cat"), strict)
        assertArrayEquals(arrayOf("dog", "legacy-only"), soft)
    }

    @Test
    fun canApplyWallpaper_falseWhenBothOff() {
        val prefs = fakePrefs(
            "HOME_STATE" to 0,
            "LOCK_STATE" to 0
        )
        assertFalse(WallpaperPrefs.canApplyWallpaper(prefs))
    }

    @Test
    fun canApplyWallpaper_trueWhenEitherOn() {
        val homeOnly = fakePrefs("HOME_STATE" to 1, "LOCK_STATE" to 0)
        val lockOnly = fakePrefs("HOME_STATE" to 0, "LOCK_STATE" to 2)
        assertTrue(WallpaperPrefs.canApplyWallpaper(homeOnly))
        assertTrue(WallpaperPrefs.canApplyWallpaper(lockOnly))
    }

    @Test
    fun isAutoUpdateEnabled_defaultsFalse() {
        assertFalse(WallpaperPrefs.isAutoUpdateEnabled(fakePrefs()))
    }

    @Test
    fun prefetchSnapshotFingerprint_changesWhenTagsDiffer() {
        val a =
            WallpaperPrefs.prefetchSnapshotFingerprint(
                styleValue = 50,
                r18Mode = 0,
                homeState = 1,
                lockState = 0,
                strictTags = arrayOf("cat"),
                softTags = emptyArray(),
            )
        val b =
            WallpaperPrefs.prefetchSnapshotFingerprint(
                styleValue = 50,
                r18Mode = 0,
                homeState = 1,
                lockState = 0,
                strictTags = arrayOf("dog"),
                softTags = emptyArray(),
            )
        assertTrue(a != b)
    }

    @Test
    fun readRecentFetchedUrls_mergesLegacySingleUrl() {
        val prefs = fakePrefs(
            WallpaperPrefs.KEY_LAST_FETCHED_URL to "https://a.example/1",
            WallpaperPrefs.KEY_RECENT_FETCHED_URLS to "https://b.example/2|https://c.example/3",
        )
        val recent = WallpaperPrefs.readRecentFetchedUrls(prefs)
        assertEquals(3, recent.size)
        assertTrue(recent.contains("https://a.example/1"))
        assertTrue(recent.contains("https://b.example/2"))
    }

    @Test
    fun readRecentFetchedUrlsList_preservesPipeOrder() {
        val prefs = fakePrefs(
            WallpaperPrefs.KEY_RECENT_FETCHED_URLS to "https://a.example/1|https://b.example/2|https://c.example/3",
        )
        val list = WallpaperPrefs.readRecentFetchedUrlsList(prefs)
        assertEquals(
            listOf(
                "https://a.example/1",
                "https://b.example/2",
                "https://c.example/3",
            ),
            list,
        )
    }

    @Test
    fun readHomeSourceUrl_returnsTrimmedValue() {
        val prefs = fakePrefs(WallpaperPrefs.KEY_HOME_SOURCE_URL to "  https://home.example/x  ")
        assertEquals("https://home.example/x", WallpaperPrefs.readHomeSourceUrl(prefs))
    }

    @Test
    fun applyOutcomeComplete_dualRequiresBothTargets() {
        val ok = com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult(homeOk = true, lockOk = true)
        val partial = com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult(homeOk = true, lockOk = false)
        assertTrue(WallpaperPrefs.applyOutcomeComplete(1, 2, ok))
        assertFalse(WallpaperPrefs.applyOutcomeComplete(1, 2, partial))
    }

    @Test
    fun applyOutcomeComplete_homeOnlyNeedsHomeOk() {
        val ok = com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult(homeOk = true, lockOk = false)
        val fail = com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult(homeOk = false, lockOk = false)
        assertTrue(WallpaperPrefs.applyOutcomeComplete(1, 0, ok))
        assertFalse(WallpaperPrefs.applyOutcomeComplete(1, 0, fail))
    }

    @Test
    fun recentUrlsForDedup_includesHomeSource() {
        val prefs = fakePrefs(
            WallpaperPrefs.KEY_RECENT_FETCHED_URLS to "https://a.example/1",
            WallpaperPrefs.KEY_HOME_SOURCE_URL to "https://b.example/home",
        )
        val recent = WallpaperPrefs.recentUrlsForDedup(prefs)
        assertEquals(2, recent.size)
        assertTrue(recent.contains("https://a.example/1"))
        assertTrue(recent.contains("https://b.example/home"))
    }

    private fun fakePrefs(vararg entries: Pair<String, Any>): SharedPreferences {
        val map = entries.toMap().toMutableMap()
        return object : SharedPreferences {
            override fun getAll(): Map<String, *> = map
            override fun getString(key: String, defValue: String?) =
                map[key] as? String ?: defValue
            override fun getStringSet(key: String, defValues: Set<String>?) =
                map[key] as? Set<String> ?: defValues
            override fun getInt(key: String, defValue: Int) =
                map[key] as? Int ?: defValue
            override fun getLong(key: String, defValue: Long) =
                map[key] as? Long ?: defValue
            override fun getFloat(key: String, defValue: Float) =
                map[key] as? Float ?: defValue
            override fun getBoolean(key: String, defValue: Boolean) =
                map[key] as? Boolean ?: defValue
            override fun contains(key: String) = map.containsKey(key)
            override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
            override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener
            ) = Unit
            override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener
            ) = Unit
        }
    }
}
