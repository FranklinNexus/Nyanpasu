package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.network.ImageUrlFallback
import com.kuroshimira.nyanpasu.network.WallpaperUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageUrlFallbackTest {

    @Test
    fun candidates_keepsPrimaryFirst() {
        val url = "https://i.pixiv.re/img-original/img/2024/01/01/12345_p0.jpg"
        val list = ImageUrlFallback.candidates(url)
        assertEquals(url, list.first())
        assertTrue(list.all { WallpaperUrlPolicy.isAllowed(it) })
    }

    @Test
    fun candidates_addsProxyForPximg() {
        val url = "https://i.pximg.net/img-original/img/2024/01/01/12345_p0.jpg"
        val list = ImageUrlFallback.candidates(url)
        assertTrue(list.contains(url))
        assertTrue(list.any { it.contains("i.pixiv.re") })
    }
}
