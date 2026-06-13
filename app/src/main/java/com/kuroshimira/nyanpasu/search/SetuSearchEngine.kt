package com.kuroshimira.nyanpasu.search

import android.content.Context as AndroidContext
import android.util.Log
import com.kuroshimira.nyanpasu.network.NetworkEnvironment
import com.kuroshimira.nyanpasu.network.NetworkRoute
import com.kuroshimira.nyanpasu.network.WallpaperImageIdentity
import com.kuroshimira.nyanpasu.network.WallpaperUrlPolicy
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * 图源分层搜索编排：Lolicon → Danbooru → Booru 降级；R18 见 [SearchR18Policy]。
 */
internal object SetuSearchEngine {

    private const val TAG = "SetuSearch"

    data class Context(
        val styleValue: Int,
        val strictTags: Array<String>,
        val softTags: Array<String>,
        val recentUrls: Set<String> = emptySet(),
    )

    private enum class SliderMode {
        OFF,
        STRICT,
        RELAXED,
    }

    private data class FetchProfile(val slider: SliderMode) {
        val useSlider: Boolean get() = slider != SliderMode.OFF
        val sliderRelaxed: Boolean get() = slider == SliderMode.RELAXED
    }

    suspend fun search(androidCtx: AndroidContext, searchCtx: Context, primaryR18: Int): String {
        val route = NetworkEnvironment.classify(androidCtx)
        Log.d(TAG, "search route=${NetworkEnvironment.logLabel(route)}")
        return searchWithRoute(searchCtx, primaryR18, route)
    }

    suspend fun searchDistinct(
        androidCtx: AndroidContext,
        searchCtx: Context,
        primaryR18: Int,
        avoidUrl: String,
    ): String {
        val route = NetworkEnvironment.classify(androidCtx)
        val patched = searchCtx.copy(recentUrls = searchCtx.recentUrls + avoidUrl)
        repeat(8) { attempt ->
            coroutineContext.ensureActive()
            val url = searchWithRoute(patched, primaryR18, route)
            if (url.isNotEmpty() && !WallpaperImageIdentity.isSameImage(url, avoidUrl)) return url
            if (attempt < 7) delay(280)
        }
        return ""
    }

    private suspend fun searchWithRoute(searchCtx: Context, primaryR18: Int, route: NetworkRoute): String {
        val normalized =
            if (searchCtx.strictTags.isEmpty() && searchCtx.softTags.isNotEmpty()) {
                searchCtx.copy(strictTags = searchCtx.softTags, softTags = emptyArray())
            } else {
                searchCtx
            }
        if (normalized.strictTags.isEmpty()) {
            return fallbackUntagged(normalized, primaryR18, normalized.recentUrls, route)
        }

        val variants = TagExpander.expand(normalized.strictTags)
        val sliderAllowed = !TagExpander.isConflictWithSlider(normalized.strictTags)
        val profileLadder = buildProfileLadder(sliderAllowed, hasStrictTags = true)
        val recent = normalized.recentUrls
        val budget = SearchTierBudget(SearchTierBudget.TOTAL_MS)

        budget.run(SearchTierBudget.TIER1_MS) {
            firstNonEmpty(normalized, primaryR18, variants, profileLadder, recent, preferFresh = true, attemptsPerCell = 2, route)
        }?.let { return it }

        if (normalized.softTags.isNotEmpty()) {
            budget.run(SearchTierBudget.TIER1A_MS) {
                searchStrictWithSoftHints(normalized, primaryR18, variants, profileLadder, recent, route)
            }?.let { return it }
        }

        budget.run(SearchTierBudget.TIER1B_MS) {
            firstNonEmpty(normalized, primaryR18, variants, profileLadder, recent, preferFresh = false, attemptsPerCell = 1, route)
        }?.let { return it }

        for (r18 in SearchR18Policy.tierEscalations(primaryR18)) {
            if (!budget.hasTime()) break
            coroutineContext.ensureActive()
            budget.run(SearchTierBudget.TIER2_MS / 2) {
                firstNonEmpty(normalized, r18, variants, profileLadder, recent, preferFresh = true, attemptsPerCell = 1, route)
            }?.let { return it }
            budget.run(SearchTierBudget.TIER2_MS / 2) {
                firstNonEmpty(normalized, r18, variants, profileLadder, recent, preferFresh = false, attemptsPerCell = 1, route)
            }?.let { return it }
        }

        budget.run(SearchTierBudget.TIER3_MS) {
            searchSliderOnly(normalized, primaryR18, recent, route)
        }?.let { return it }

        return budget.run(SearchTierBudget.TIER4_MS) {
            fallbackUntagged(normalized, primaryR18, recent, route)
        } ?: ""
    }

    private fun buildProfileLadder(sliderAllowed: Boolean, hasStrictTags: Boolean): List<FetchProfile> =
        buildList {
            if (!sliderAllowed) {
                add(FetchProfile(SliderMode.OFF))
                return@buildList
            }
            if (hasStrictTags) {
                add(FetchProfile(SliderMode.OFF))
                add(FetchProfile(SliderMode.RELAXED))
                add(FetchProfile(SliderMode.STRICT))
            } else {
                add(FetchProfile(SliderMode.STRICT))
                add(FetchProfile(SliderMode.OFF))
                add(FetchProfile(SliderMode.RELAXED))
            }
        }

    private suspend fun firstNonEmpty(
        ctx: Context,
        r18: Int,
        variants: List<List<String>>,
        profiles: List<FetchProfile>,
        recentUrls: Set<String>,
        preferFresh: Boolean,
        attemptsPerCell: Int,
        route: NetworkRoute,
    ): String? {
        for (variant in variants.shuffled()) {
            coroutineContext.ensureActive()
            for (profile in profiles) {
                repeat(attemptsPerCell) {
                    val url =
                        SetuFetchPipeline.buildAndFetch(
                            variant,
                            ctx.softTags,
                            ctx.styleValue,
                            r18,
                            profile.useSlider,
                            profile.sliderRelaxed,
                            ctx.strictTags,
                            route,
                        )
                    if (accept(url, recentUrls, preferFresh)) {
                        Log.d(TAG, "hit r18=$r18 variant=$variant profile=$profile")
                        return url
                    }
                    delay(24)
                }
            }
        }
        return null
    }

    private suspend fun searchStrictWithSoftHints(
        ctx: Context,
        r18: Int,
        variants: List<List<String>>,
        profiles: List<FetchProfile>,
        recentUrls: Set<String>,
        route: NetworkRoute,
    ): String? {
        for (variant in variants.shuffled()) {
            coroutineContext.ensureActive()
            for (profile in profiles) {
                for (soft in ctx.softTags) {
                    val url =
                        SetuFetchPipeline.buildAndFetch(
                            primaryTags = variant,
                            softTags = arrayOf(soft),
                            styleValue = ctx.styleValue,
                            r18Mode = r18,
                            useSlider = profile.useSlider,
                            sliderRelaxed = profile.sliderRelaxed,
                            intentSourceTags = ctx.strictTags,
                            attachSoftTag = true,
                            route = route,
                        )
                    if (accept(url, recentUrls, preferFresh = true)) {
                        Log.d(TAG, "hit strict+soft soft=$soft variant=$variant")
                        return url
                    }
                    delay(18)
                }
            }
        }
        return null
    }

    private suspend fun searchSliderOnly(
        ctx: Context,
        primaryR18: Int,
        recentUrls: Set<String>,
        route: NetworkRoute,
    ): String? {
        for (r18 in SearchR18Policy.fallbackLoliconChain(primaryR18)) {
            coroutineContext.ensureActive()
            for (relaxed in listOf(false, true)) {
                repeat(2) {
                    val url =
                        SetuFetchPipeline.buildAndFetch(
                            emptyList(),
                            ctx.softTags,
                            ctx.styleValue,
                            r18,
                            useSlider = true,
                            sliderRelaxed = relaxed,
                            ctx.strictTags,
                            route,
                        )
                    if (accept(url, recentUrls, preferFresh = true)) return url
                    delay(18)
                }
            }
            for (relaxed in listOf(false, true)) {
                val url =
                    SetuFetchPipeline.buildAndFetch(
                        emptyList(),
                        ctx.softTags,
                        ctx.styleValue,
                        r18,
                        useSlider = true,
                        sliderRelaxed = relaxed,
                        ctx.strictTags,
                        route,
                    )
                if (accept(url, recentUrls, preferFresh = false)) return url
                delay(18)
            }
        }
        return null
    }

    private suspend fun fallbackUntagged(
        ctx: Context,
        primaryR18: Int,
        recentUrls: Set<String>,
        route: NetworkRoute,
    ): String {
        for (r18 in SearchR18Policy.fallbackLoliconChain(primaryR18)) {
            coroutineContext.ensureActive()
            for (relaxed in listOf(false, true)) {
                repeat(2) {
                    val url =
                        SetuFetchPipeline.buildAndFetch(
                            emptyList(),
                            ctx.softTags,
                            ctx.styleValue,
                            r18,
                            useSlider = true,
                            sliderRelaxed = relaxed,
                            ctx.strictTags,
                            route,
                        )
                    if (accept(url, recentUrls, preferFresh = true)) return url
                    delay(18)
                }
            }
            val bare =
                SetuFetchPipeline.buildAndFetch(
                    emptyList(),
                    ctx.softTags,
                    ctx.styleValue,
                    r18,
                    useSlider = false,
                    sliderRelaxed = false,
                    ctx.strictTags,
                    route,
                )
            if (accept(bare, recentUrls, preferFresh = true)) return bare
            repeat(2) {
                val url = SetuFetchPipeline.fetchBare(r18, route)
                if (accept(url, recentUrls, preferFresh = true)) return url
            }
        }
        return ""
    }

    private fun accept(url: String, recentUrls: Set<String>, preferFresh: Boolean): Boolean {
        if (url.isEmpty()) return false
        if (!WallpaperUrlPolicy.isAllowed(url)) {
            Log.w(TAG, "reject disallowed url host")
            return false
        }
        if (preferFresh && recentUrls.isNotEmpty() &&
            !WallpaperImageIdentity.differsFromAny(url, recentUrls)
        ) {
            return false
        }
        return true
    }
}
