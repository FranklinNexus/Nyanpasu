package com.kuroshimira.nyanpasu.network

import android.net.Uri

/** 图源下载失败时的镜像/反代候选（保持 https + 白名单内）。 */
object ImageUrlFallback {

    private val PIXIV_PROXY_HOSTS =
        listOf(
            "i.pixiv.re",
            "i.pixiv.nl",
            "pixiv.yuki.sh",
        )

    fun candidates(primary: String): List<String> {
        if (primary.isBlank()) return emptyList()
        val ordered = linkedSetOf(primary.trim())
        val uri = Uri.parse(primary)
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.encodedPath?.takeIf { it.isNotEmpty() } ?: return filterAllowed(ordered)

        if (host.contains("pximg.net") || host.contains("pixiv.net")) {
            for (proxy in PIXIV_PROXY_HOSTS) {
                ordered.add("https://$proxy$path")
            }
        }
        if (host.endsWith(".pixiv.re") || host.endsWith(".pixiv.nl")) {
            val barePath = path
            for (proxy in PIXIV_PROXY_HOSTS) {
                if (host != proxy) ordered.add("https://$proxy$barePath")
            }
        }
        return filterAllowed(ordered)
    }

    private fun filterAllowed(urls: Iterable<String>): List<String> =
        urls.filter { WallpaperUrlPolicy.isAllowed(it) }.distinct()
}
