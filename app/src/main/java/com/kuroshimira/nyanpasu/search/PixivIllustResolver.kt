package com.kuroshimira.nyanpasu.search

import android.util.Log
import com.kuroshimira.nyanpasu.network.AppHttpClient
import org.json.JSONObject

/**
 * 将 Pixiv 作品 ID 解析为可直接下载的高清图 URL。
 * 优先官方 Ajax（regular/original），失败时用公共镜像线路兜底。
 */
object PixivIllustResolver {

    private const val TAG = "PixivIllustResolver"
    private const val AJAX = "https://www.pixiv.net/ajax/illust/"

    private val MIRROR_TEMPLATES = listOf(
        "https://pixiv.cat/%d.jpg",
        "https://pixiv.cat/%d@master1200.jpg",
    )

    suspend fun bestImageUrl(
        pixivId: Long,
        fallback: String = "",
        preferMirrorFirst: Boolean = false,
    ): String {
        if (pixivId <= 0L) return fallback
        if (preferMirrorFirst) {
            resolveViaMirror(pixivId)?.let { return it }
            resolveViaAjax(pixivId)?.let { return it }
        } else {
            resolveViaAjax(pixivId)?.let { return it }
            resolveViaMirror(pixivId)?.let { return it }
        }
        return fallback
    }

    private suspend fun resolveViaAjax(pixivId: Long): String? {
        return try {
            val text =
                AppHttpClient.getStringResilient(
                    "$AJAX$pixivId",
                    mapOf("Accept" to "application/json"),
                ) ?: return null
            val urls =
                JSONObject(text)
                    .optJSONObject("body")
                    ?.optJSONObject("urls")
                    ?: return null
            listOf("regular", "original")
                .mapNotNull { key -> urls.optString(key, "").takeIf { it.isNotEmpty() } }
                .firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "ajax illust $pixivId: ${e.message}")
            null
        }
    }

    private suspend fun resolveViaMirror(pixivId: Long): String? {
        val candidates = MIRROR_TEMPLATES.map { it.format(pixivId) }.shuffled()
        for (url in candidates) {
            if (AppHttpClient.probeUrl(url)) {
                Log.d(TAG, "mirror ok pixiv=$pixivId")
                return url
            }
        }
        return null
    }
}
