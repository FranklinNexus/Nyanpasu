package com.kuroshimira.nyanpasu.network

/**
 * 跨 CDN / 镜像 URL 识别是否为同一张图（主要按 Pixiv pid）。
 * 粉+蓝独立锁屏、去重 recent 时不能只做字符串比较。
 */
object WallpaperImageIdentity {

    private val PIXIV_CAT = Regex("""(?i)pixiv\.cat/(\d+)""")
    private val PXIMG_PAGE = Regex("""(?i)/(\d+)_p\d+(?:\.\w+)?(?:\?|$)""")
    private val LOLI_GG = Regex("""(?i)loli\.gg/(\d+)""")

    fun dedupeKey(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        PIXIV_CAT.find(trimmed)?.groupValues?.get(1)?.let { return "pixiv:$it" }
        PXIMG_PAGE.find(trimmed)?.groupValues?.get(1)?.let { return "pixiv:$it" }
        LOLI_GG.find(trimmed)?.groupValues?.get(1)?.let { return "pixiv:$it" }
        return "url:${trimmed.substringBefore('?').lowercase()}"
    }

    fun isSameImage(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return dedupeKey(a) == dedupeKey(b)
    }

    fun differsFromAny(url: String, others: Collection<String>): Boolean =
        others.none { isSameImage(url, it) }
}
