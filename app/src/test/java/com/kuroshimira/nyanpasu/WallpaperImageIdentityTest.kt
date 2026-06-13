package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.network.WallpaperImageIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperImageIdentityTest {

    @Test
    fun samePixivId_differentHosts() {
        val a = "https://pixiv.cat/12345678.jpg"
        val b = "https://i.pximg.net/img-master/img/2024/01/01/12345678_p0.jpg"
        assertTrue(WallpaperImageIdentity.isSameImage(a, b))
    }

    @Test
    fun differentPixivIds() {
        val a = "https://pixiv.cat/111.jpg"
        val b = "https://pixiv.cat/222.jpg"
        assertFalse(WallpaperImageIdentity.isSameImage(a, b))
    }

    @Test
    fun differsFromAny_usesIdentity() {
        val recent = setOf("https://pixiv.cat/99.jpg")
        assertFalse(
            WallpaperImageIdentity.differsFromAny(
                "https://i.pximg.net/img/2024/99_p0.jpg",
                recent,
            ),
        )
    }
}
