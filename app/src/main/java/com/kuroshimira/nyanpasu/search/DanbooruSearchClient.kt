package com.kuroshimira.nyanpasu.search

import android.util.Log
import com.kuroshimira.nyanpasu.network.AppHttpClient
import java.net.URLEncoder
import kotlin.random.Random
import org.json.JSONArray

/** Danbooru JSON + Moe/Gel Booru 池兜底。 */
internal object DanbooruSearchClient {

    private const val POSTS_JSON = "https://danbooru.donmai.us/posts.json"
    private const val TAG = "SetuSearch"

    private data class Pick(val url: String, val pixivId: Long)

    suspend fun fetchPixiv(
        primaryTags: List<String>,
        softTags: Array<String>,
        styleValue: Int,
        r18Mode: Int,
        useSlider: Boolean,
        sliderRelaxed: Boolean,
        intentSourceTags: Array<String>,
        requirePixivId: Boolean,
        compactTags: Boolean,
    ): String {
        val intentBlobLower = SearchScoring.intentBlob(intentSourceTags, primaryTags)
        val parts = ArrayList<String>()
        if (compactTags) {
            ratingClause(r18Mode)?.let { parts.add(it) }
            if (requirePixivId) parts.add("pixiv_id:>0")
            parts.add("order:random")
            val raw =
                primaryTags.firstOrNull()?.trim()?.let { tok ->
                    if ("|" in tok) tok.substringBefore("|").trim() else tok
                }?.replace(' ', '_')?.lowercase()
            if (!raw.isNullOrEmpty()) parts.add(raw)
        } else {
            ratingClause(r18Mode)?.let { parts.add(it) }
            if (requirePixivId) parts.add("pixiv_id:>0")
            parts.add("order:random")
            primaryTags.forEach { parts.addAll(tokenToTerms(it)) }
            if (useSlider) {
                bodySlideTerms(SearchScoring.styleBand(styleValue), sliderRelaxed)?.let { parts.add(it) }
            }
            if (primaryTags.isEmpty() && softTags.isNotEmpty() && Random.nextInt(100) < 40) {
                parts.addAll(tokenToTerms(softTags.random()))
            }
            for (spec in SearchNicheRegistry.specs) {
                if (spec.matchesIntentBlob(intentBlobLower)) {
                    spec.danbooruBoostOrs.forEach { parts.add(it) }
                }
            }
        }
        val tagQuery = parts.filter { it.isNotBlank() }.joinToString(" ")
        if (tagQuery.length > 1500) return ""
        val url = "$POSTS_JSON?limit=25&tags=${encode(tagQuery)}"
        return fetchInternal(url, intentBlobLower, requirePixivId, r18Mode)
    }

    suspend fun fetchBooruPool(
        primaryTags: List<String>,
        intentSourceTags: Array<String>,
        r18Mode: Int,
    ): String {
        val blob = SearchScoring.intentBlob(intentSourceTags, primaryTags)
        val niche = SearchNicheRegistry.specs.firstOrNull { it.matchesIntentBlob(blob) }
        val tags = BooruPoolClient.buildTagCandidates(primaryTags, niche?.fallbackSingles ?: emptyList())
        val rated = tags.map { tag -> BooruPoolClient.ratingSuffix(r18Mode)?.let { "$tag $it" } ?: tag }
        var url = BooruPoolClient.fetchMoebooru(rated, r18Mode)
        if (url.isEmpty()) {
            url = BooruPoolClient.fetchGelbooru(tags, r18Mode)
        }
        return url
    }

    private fun ratingClause(r18Mode: Int): String? =
        when (r18Mode) {
            0 -> "rating:general"
            1 -> "-rating:general"
            else -> "-rating:explicit"
        }

    private fun tokenToTerms(token: String): List<String> {
        val t = token.trim()
        if (t.isEmpty()) return emptyList()
        if ("|" in t) {
            val ors =
                t.split("|")
                    .map { it.trim().replace(' ', '_').lowercase() }
                    .filter { it.isNotEmpty() }
            if (ors.isEmpty()) return emptyList()
            return listOf(ors.joinToString(" ") { "~$it" })
        }
        return listOf(t.replace(' ', '_').lowercase())
    }

    private fun bodySlideTerms(band: SearchScoring.StyleBand, relaxed: Boolean): String? =
        when (band) {
            SearchScoring.StyleBand.SMALL ->
                if (relaxed) "~small_breasts ~flat_chest ~petite ~medium_breasts"
                else "~small_breasts ~flat_chest ~petite"
            SearchScoring.StyleBand.LARGE ->
                if (relaxed) "~huge_breasts ~large_breasts ~gigantic_breasts ~medium_breasts"
                else "~huge_breasts ~large_breasts ~gigantic_breasts"
            SearchScoring.StyleBand.NEUTRAL -> null
        }

    private suspend fun fetchInternal(
        urlString: String,
        intentBlobLower: String,
        requirePixivId: Boolean,
        appR18Mode: Int,
    ): String {
        return try {
            val response = AppHttpClient.getString(urlString) ?: return ""
            val arr = JSONArray(response)
            if (arr.length() == 0) return ""
            val scored = ArrayList<Pair<Pick, Int>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("is_deleted")) continue
                val ratingChar = o.optString("rating", "")
                when (appR18Mode) {
                    0 -> if (ratingChar != "g") continue
                    2 -> if (ratingChar == "e") continue
                }
                val pix = o.optLong("pixiv_id", 0L)
                if (requirePixivId && pix <= 0L) continue
                val fileUrl = o.optString("file_url", "")
                if (fileUrl.isEmpty()) continue
                val tagRaw = o.optString("tag_string", "")
                var sc = 1 + SearchScoring.scoreTagsForNiches(tagRaw, intentBlobLower)
                if (!requirePixivId && pix > 0L) sc += 1
                scored.add(Pick(fileUrl, pix) to sc)
            }
            if (scored.isEmpty()) return ""
            pickFromScored(scored, intentBlobLower)
        } catch (e: Exception) {
            Log.e(TAG, "danbooru: ${e.message}")
            ""
        }
    }

    private suspend fun pickFromScored(
        scored: List<Pair<Pick, Int>>,
        intentBlobLower: String,
    ): String {
        if (scored.isEmpty()) return ""
        suspend fun finalize(pick: Pick): String =
            PixivIllustResolver.bestImageUrl(pick.pixivId, pick.url)

        val nicheApplied = SearchNicheRegistry.specs.any { it.matchesIntentBlob(intentBlobLower) }
        if (nicheApplied) {
            scored.filter { it.second >= 2 }.takeIf { it.isNotEmpty() }?.let {
                return finalize(it[Random.nextInt(it.size)].first)
            }
            scored.filter { it.second >= 1 }.takeIf { it.isNotEmpty() }?.let {
                Log.d(TAG, "danbooru niche soft-pick")
                return finalize(it[Random.nextInt(it.size)].first)
            }
            Log.w(TAG, "danbooru niche last-resort pick")
            val pool = scored.filter { it.second > 0 }.ifEmpty { scored }
            return finalize(pool[Random.nextInt(pool.size)].first)
        }
        val pool = scored.filter { it.second > 0 }.ifEmpty { scored }
        return finalize(pool[Random.nextInt(pool.size)].first)
    }

    private fun encode(str: String): String =
        try {
            URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
}
