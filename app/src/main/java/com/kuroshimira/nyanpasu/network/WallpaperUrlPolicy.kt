package com.kuroshimira.nyanpasu.network

import android.net.Uri
import android.util.Log

/** 壁纸下载前域名白名单：仅允许搜索管线可能返回的图源 CDN。 */
object WallpaperUrlPolicy {

    private const val TAG = "WallpaperUrlPolicy"

    private val ALLOWED_HOST_SUFFIXES =
        listOf(
            "donmai.us",
            "pixiv.net",
            "pximg.net",
            "pixiv.cat",
            "pixiv.re",
            "lolicon.app",
            "yetal.ml",
            "teimg.com",
            "konachan.net",
            "konachan.com",
            "behoimi.org",
            "yande.re",
            "safebooru.org",
            "tbib.org",
            "gelbooru.com",
            "xbooru.org",
            // Booru / 镜像常见外链 CDN
            "catbox.moe",
            "imgur.com",
            "lolisafe.moe",
        )

    fun isAllowed(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = Uri.parse(url).host?.lowercase() ?: return false
        val ok = isHostAllowed(host)
        if (!ok) {
            Log.w(TAG, "blocked download host: $host")
        }
        return ok
    }

    internal fun isHostAllowed(host: String): Boolean =
        ALLOWED_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
}
