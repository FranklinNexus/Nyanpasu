package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.network.WallpaperUrlPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperUrlPolicyTest {

    @Test
    fun isHostAllowed_pixivRe() {
        assertTrue(WallpaperUrlPolicy.isHostAllowed("i.pixiv.re"))
    }

    @Test
    fun isHostAllowed_subdomain() {
        assertTrue(WallpaperUrlPolicy.isHostAllowed("i.pximg.net"))
    }

    @Test
    fun isHostAllowed_rejectsUnknown() {
        assertFalse(WallpaperUrlPolicy.isHostAllowed("evil.example.com"))
    }

    @Test
    fun isAllowed_requiresHttps() {
        assertFalse(WallpaperUrlPolicy.isAllowed("http://i.pixiv.re/img.jpg"))
    }
}
