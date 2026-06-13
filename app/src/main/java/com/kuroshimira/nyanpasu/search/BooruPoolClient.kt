package com.kuroshimira.nyanpasu.search
import com.kuroshimira.nyanpasu.network.AppHttpClient

import android.util.Log
import java.net.URLEncoder
import kotlin.random.Random
import org.json.JSONArray

/**
 * 多线路 Booru 兜底：Moebooru 系 + Gelbooru 系，扩大 Tag 命中池。
 */
object BooruPoolClient {

    private const val TAG = "BooruPoolClient"

    private data class MoeSite(val endpoint: String, val ratingFilter: RatingFilter)

    private enum class RatingFilter { SAFE_ONLY, EXPLICIT_ONLY, ANY }

    private val MOE_PURE = listOf(
        MoeSite("https://konachan.net/post.json", RatingFilter.SAFE_ONLY),
        MoeSite("https://behoimi.org/post.json", RatingFilter.SAFE_ONLY),
    )

    private val MOE_NSFW = listOf(
        MoeSite("https://konachan.com/post.json", RatingFilter.EXPLICIT_ONLY),
        MoeSite("https://yande.re/post.json", RatingFilter.ANY),
    )

    private val MOE_MIX = listOf(
        MoeSite("https://yande.re/post.json", RatingFilter.ANY),
        MoeSite("https://konachan.com/post.json", RatingFilter.ANY),
        MoeSite("https://konachan.net/post.json", RatingFilter.ANY),
    )

    /** Gelbooru 系 JSON API（免 OAuth）。 */
    private val GEL_PURE = listOf(
        "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1",
        "https://tbib.org/index.php?page=dapi&s=post&q=index&json=1",
    )

    private val GEL_NSFW = listOf(
        "https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1",
        "https://xbooru.com/index.php?page=dapi&s=post&q=index&json=1",
    )

    suspend fun fetchMoebooru(tagCandidates: List<String>, r18Mode: Int): String {
        val sites =
            when (r18Mode) {
                0 -> MOE_PURE
                1 -> MOE_NSFW
                else -> MOE_MIX
            }
        for (tag in tagCandidates) {
            val query = tag.trim()
            if (query.isEmpty()) continue
            for (site in sites) {
                fetchMoeJson(site.endpoint, query, site.ratingFilter, r18Mode)?.let { return it }
            }
        }
        return ""
    }

    suspend fun fetchGelbooru(tagCandidates: List<String>, r18Mode: Int): String {
        val bases =
            when (r18Mode) {
                0 -> GEL_PURE
                1 -> GEL_NSFW
                else -> GEL_PURE + GEL_NSFW
            }
        for (tag in tagCandidates) {
            val normalized = tag.trim().replace(' ', '_').lowercase()
            if (normalized.isEmpty()) continue
            for (base in bases) {
                fetchGelbooruJson(base, normalized, r18Mode)?.let { return it }
            }
        }
        return ""
    }

    fun buildTagCandidates(
        primaryTags: List<String>,
        fallbackSingles: List<String> = emptyList(),
    ): List<String> {
        val out = LinkedHashSet<String>()
        primaryTags.forEach { raw ->
            val p = raw.trim()
            if (p.isEmpty()) return@forEach
            val one = (if ("|" in p) p.substringBefore("|") else p).replace(' ', '_').lowercase()
            if (one.isNotEmpty()) out.add(one)
            p.split("|").forEach { part ->
                val t = part.trim().replace(' ', '_').lowercase()
                if (t.isNotEmpty()) out.add(t)
            }
        }
        fallbackSingles.forEach {
            val t = it.trim().replace(' ', '_').lowercase()
            if (t.isNotEmpty()) out.add(t)
        }
        if (out.isEmpty()) out.add("1girl")
        return out.toList()
    }

    fun ratingSuffix(r18Mode: Int): String? =
        when (r18Mode) {
            0 -> "rating:safe"
            1 -> "rating:explicit"
            else -> "-rating:explicit"
        }

    private suspend fun fetchMoeJson(
        endpoint: String,
        tagQuery: String,
        filter: RatingFilter,
        r18Mode: Int,
    ): String? {
        return try {
            val urlStr = "$endpoint?limit=24&tags=${encode(tagQuery)}"
            val body = AppHttpClient.getString(urlStr) ?: return null
            val arr = JSONArray(body)
            pickMoeUrl(arr, filter, r18Mode)?.also {
                Log.d(TAG, "moe hit $endpoint tags=$tagQuery")
            }
        } catch (e: Exception) {
            Log.w(TAG, "moe $endpoint: ${e.message}")
            null
        }
    }

    private fun pickMoeUrl(arr: JSONArray, filter: RatingFilter, r18Mode: Int): String? {
        val urls = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rt = normalizeMoeRating(o.optString("rating", ""))
            when (filter) {
                RatingFilter.SAFE_ONLY -> if (rt != "safe") continue
                RatingFilter.EXPLICIT_ONLY -> if (rt != "explicit") continue
                RatingFilter.ANY -> if (!moeRatingAllowed(rt, r18Mode)) continue
            }
            val pick =
                sequenceOf(
                    o.optString("sample_url", ""),
                    o.optString("jpeg_url", ""),
                    o.optString("file_url", ""),
                ).firstOrNull { it.isNotEmpty() } ?: continue
            urls.add(pick)
        }
        return urls.takeIf { it.isNotEmpty() }?.let { it[Random.nextInt(it.size)] }
    }

    private suspend fun fetchGelbooruJson(base: String, tag: String, r18Mode: Int): String? {
        return try {
            val urlStr = "$base&tags=${encode(tag)}&limit=24"
            val body = AppHttpClient.getString(urlStr) ?: return null
            val arr = JSONArray(body)
            val urls = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val rating = o.optString("rating", "").lowercase()
                if (!gelRatingAllowed(rating, r18Mode)) continue
                val file = o.optString("file_url", "")
                if (file.isNotEmpty()) urls.add(file)
            }
            urls.takeIf { it.isNotEmpty() }?.let { pool ->
                Log.d(TAG, "gel hit $base tag=$tag")
                pool[Random.nextInt(pool.size)]
            }
        } catch (e: Exception) {
            Log.w(TAG, "gel $base: ${e.message}")
            null
        }
    }

    private fun moeRatingAllowed(rating: String, r18Mode: Int): Boolean {
        val rt = normalizeMoeRating(rating)
        return when (r18Mode) {
            0 -> rt == "safe"
            1 -> rt != "safe"
            else -> rt != "explicit"
        }
    }

    /** Moebooru API 返回单字母分级（s/q/e），与 Danbooru 全名混用时需归一化。 */
    internal fun normalizeMoeRating(rating: String): String =
        when (rating.lowercase()) {
            "s", "safe" -> "safe"
            "q", "questionable" -> "questionable"
            "e", "explicit" -> "explicit"
            else -> rating.lowercase()
        }

    private fun gelRatingAllowed(rating: String, r18Mode: Int): Boolean =
        when (r18Mode) {
            0 -> rating !in EXPLICIT_GEL_RATINGS && rating != "q"
            1 -> true
            else -> rating !in EXPLICIT_GEL_RATINGS
        }

    private val EXPLICIT_GEL_RATINGS = setOf("explicit", "e")

    private fun encode(str: String): String =
        try {
            URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
}
