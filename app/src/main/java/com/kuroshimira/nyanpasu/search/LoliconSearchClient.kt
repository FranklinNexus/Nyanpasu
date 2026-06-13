package com.kuroshimira.nyanpasu.search

import android.util.Log
import com.kuroshimira.nyanpasu.network.AppHttpClient
import com.kuroshimira.nyanpasu.network.NetworkEnvironment
import com.kuroshimira.nyanpasu.network.NetworkRoute
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import java.net.URLEncoder
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/** Lolicon /setu/v2 多镜像拉取。 */
internal object LoliconSearchClient {

    private const val NUM = 16
    private const val TAG = "SetuSearch"

    suspend fun fetchTagged(
        primaryTags: List<String>,
        softTags: Array<String>,
        styleValue: Int,
        r18Mode: Int,
        useSlider: Boolean,
        sliderRelaxed: Boolean,
        intentSourceTags: Array<String>,
        route: NetworkRoute = NetworkRoute.NORMAL,
        attachSoftTag: Boolean = false,
    ): String {
        val intentBlobLower = SearchScoring.intentBlob(intentSourceTags, primaryTags)
        val dropR18Items = SearchR18Policy.skipLoliconItemsMarkedR18(r18Mode)

        suspend fun tryOnce(aspect: Boolean, excludeAi: Boolean): String =
            fetchPath(
                buildPath(
                    primaryTags,
                    softTags,
                    styleValue,
                    r18Mode,
                    useSlider,
                    sliderRelaxed,
                    withAspectRatio = aspect,
                    withExcludeAi = excludeAi,
                    attachSoftTag = attachSoftTag,
                ),
                intentBlobLower,
                dropR18Items,
                route,
            )

        return if (primaryTags.isNotEmpty()) {
            var url = tryOnce(aspect = false, excludeAi = true)
            if (url.isEmpty()) url = tryOnce(aspect = false, excludeAi = false)
            if (url.isEmpty()) url = tryOnce(aspect = true, excludeAi = true)
            url
        } else {
            var url = tryOnce(aspect = true, excludeAi = true)
            if (url.isEmpty()) url = tryOnce(aspect = false, excludeAi = true)
            if (url.isEmpty()) url = tryOnce(aspect = false, excludeAi = false)
            url
        }
    }

    suspend fun fetchBare(r18Mode: Int, route: NetworkRoute = NetworkRoute.NORMAL): String {
        val path =
            buildPath(
                primaryTags = emptyList(),
                softTags = emptyArray(),
                styleValue = WallpaperPrefs.DEFAULT_STYLE,
                r18Mode = r18Mode,
                useSlider = false,
                sliderRelaxed = false,
                withAspectRatio = false,
                withExcludeAi = true,
            )
        return fetchPath(path, "", SearchR18Policy.skipLoliconItemsMarkedR18(r18Mode), route)
    }

    private fun buildPath(
        primaryTags: List<String>,
        softTags: Array<String>,
        styleValue: Int,
        r18Mode: Int,
        useSlider: Boolean,
        sliderRelaxed: Boolean,
        withAspectRatio: Boolean,
        withExcludeAi: Boolean,
        attachSoftTag: Boolean = false,
    ): String {
        val params = StringBuilder("?r18=$r18Mode&size=regular")
        val band = SearchScoring.styleBand(styleValue)
        val primarySlots = primaryTagSlots(primaryTags, useSlider, band, sliderRelaxed)
        primarySlots.forEach { params.append("&tag=${encode(it)}") }
        if (useSlider) {
            SearchScoring.sliderTagGroup(band, sliderRelaxed)?.let { params.append("&tag=${encode(it)}") }
        }
        if (primarySlots.isNotEmpty() && softTags.isNotEmpty()) {
            val chance = if (attachSoftTag) 100 else 38
            if (Random.nextInt(100) < chance) {
                params.append("&tag=${encode(softTags.random())}")
            }
        } else if (primarySlots.isEmpty() && softTags.isNotEmpty() && Random.nextInt(100) < 38) {
            params.append("&tag=${encode(softTags.random())}")
        }
        if (withExcludeAi) params.append("&excludeAI=true")
        if (withAspectRatio) params.append("&aspectRatio=lt1")
        params.append("&num=$NUM")
        return params.toString()
    }

    private fun primaryTagSlots(
        primaryTags: List<String>,
        useSlider: Boolean,
        band: SearchScoring.StyleBand,
        sliderRelaxed: Boolean,
    ): List<String> {
        val mapped = primaryTags.map { it.trim() }.filter { it.isNotEmpty() }
        if (mapped.isEmpty()) return emptyList()
        val sliderOccupiesSlot = useSlider && SearchScoring.sliderTagGroup(band, sliderRelaxed) != null
        val maxPrimary = if (sliderOccupiesSlot) 2 else 3
        return mapped.take(maxPrimary)
    }

    private suspend fun fetchPath(
        queryPath: String,
        intentBlobLower: String,
        dropMarkedR18: Boolean,
        route: NetworkRoute,
    ): String {
        for (base in NetworkEnvironment.loliconMirrorOrder(route)) {
            val hit = fetchJson(base + queryPath, intentBlobLower, dropMarkedR18, route)
            if (hit.isNotEmpty()) {
                Log.d(TAG, "lolicon mirror: $base route=${NetworkEnvironment.logLabel(route)}")
                return hit
            }
        }
        return ""
    }

    private suspend fun fetchJson(
        urlString: String,
        intentBlobLower: String,
        dropMarkedR18: Boolean,
        route: NetworkRoute,
    ): String {
        return try {
            val response = AppHttpClient.getStringResilient(urlString) ?: return ""
            val dataArray = JSONObject(response).optJSONArray("data") ?: return ""
            pickRegularUrl(dataArray, intentBlobLower, dropMarkedR18, route)
        } catch (e: Exception) {
            Log.e(TAG, "lolicon: ${e.message}")
            ""
        }
    }

    private suspend fun pickRegularUrl(
        dataArray: JSONArray,
        intentBlobLower: String,
        dropMarkedR18: Boolean,
        route: NetworkRoute,
    ): String {
        if (dataArray.length() == 0) return ""
        val entries = ArrayList<Pair<JSONObject, Int>>(dataArray.length())
        val relevance = SearchNicheRegistry.specs.any { it.matchesIntentBlob(intentBlobLower) }
        for (i in 0 until dataArray.length()) {
            val o = dataArray.optJSONObject(i) ?: continue
            if (dropMarkedR18 && o.optBoolean("r18", false)) continue
            val urls = o.optJSONObject("urls") ?: continue
            val reg = urls.optString("regular", "")
            if (reg.isEmpty()) continue
            val tagStr = joinTags(o.optJSONArray("tags"))
            entries.add(o to SearchScoring.scoreTagsForNiches(tagStr, intentBlobLower))
        }
        if (entries.isEmpty()) return ""
        if (relevance) {
            entries.filter { it.second >= 1 }.takeIf { it.isNotEmpty() }?.let {
                return chosenUrl(it[Random.nextInt(it.size)].first, route)
            }
            entries.filter { it.second >= 0 }.takeIf { it.isNotEmpty() }?.let {
                Log.d(TAG, "lolicon niche soft-pick")
                return chosenUrl(it[Random.nextInt(it.size)].first, route)
            }
            Log.w(TAG, "lolicon niche last-resort random")
            return chosenUrl(entries[Random.nextInt(entries.size)].first, route)
        }
        return chosenUrl(entries[Random.nextInt(entries.size)].first, route)
    }

    private suspend fun chosenUrl(chosen: JSONObject, route: NetworkRoute): String {
        val reg = chosen.getJSONObject("urls").getString("regular")
        val pid = chosen.optLong("pid", 0L)
        val preferMirror = NetworkEnvironment.preferPixivMirrorFirst(route)
        return PixivIllustResolver.bestImageUrl(pid, reg, preferMirrorFirst = preferMirror)
    }

    private fun joinTags(tags: JSONArray?): String {
        if (tags == null || tags.length() == 0) return ""
        return buildString {
            for (i in 0 until tags.length()) {
                if (i > 0) append(' ')
                append(tags.optString(i))
            }
        }
    }

    private fun encode(str: String): String =
        try {
            URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
}
