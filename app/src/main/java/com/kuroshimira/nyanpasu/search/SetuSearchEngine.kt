package com.kuroshimira.nyanpasu.search

import android.util.Log
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

    suspend fun search(ctx: Context, primaryR18: Int): String {
        val searchCtx =
            if (ctx.strictTags.isEmpty() && ctx.softTags.isNotEmpty()) {
                ctx.copy(strictTags = ctx.softTags, softTags = emptyArray())
            } else {
                ctx
            }
        if (searchCtx.strictTags.isEmpty()) {
            return fallbackUntagged(ctx, primaryR18)
        }

        val variants = TagExpander.expand(searchCtx.strictTags)
        val sliderAllowed = !TagExpander.isConflictWithSlider(searchCtx.strictTags)
        val profileLadder = buildProfileLadder(sliderAllowed, hasStrictTags = true)
        val recent = searchCtx.recentUrls
        val budget = SearchTierBudget(SearchTierBudget.TOTAL_MS)

        budget.run(SearchTierBudget.TIER1_MS) {
            firstNonEmpty(searchCtx, primaryR18, variants, profileLadder, recent, preferFresh = true, attemptsPerCell = 2)
        }?.let { return it }

        if (searchCtx.softTags.isNotEmpty()) {
            budget.run(SearchTierBudget.TIER1A_MS) {
                searchStrictWithSoftHints(searchCtx, primaryR18, variants, profileLadder, recent)
            }?.let { return it }
        }

        budget.run(SearchTierBudget.TIER1B_MS) {
            firstNonEmpty(searchCtx, primaryR18, variants, profileLadder, recent, preferFresh = false, attemptsPerCell = 1)
        }?.let { return it }

        for (r18 in SearchR18Policy.tierEscalations(primaryR18)) {
            if (!budget.hasTime()) break
            coroutineContext.ensureActive()
            budget.run(SearchTierBudget.TIER2_MS / 2) {
                firstNonEmpty(searchCtx, r18, variants, profileLadder, recent, preferFresh = true, attemptsPerCell = 1)
            }?.let { return it }
            budget.run(SearchTierBudget.TIER2_MS / 2) {
                firstNonEmpty(searchCtx, r18, variants, profileLadder, recent, preferFresh = false, attemptsPerCell = 1)
            }?.let { return it }
        }

        budget.run(SearchTierBudget.TIER3_MS) {
            searchSliderOnly(searchCtx, primaryR18, recent)
        }?.let { return it }

        return budget.run(SearchTierBudget.TIER4_MS) {
            fallbackUntagged(searchCtx, primaryR18)
        } ?: ""
    }

    suspend fun searchDistinct(ctx: Context, primaryR18: Int, avoidUrl: String): String {
        val patched = ctx.copy(recentUrls = ctx.recentUrls + avoidUrl)
        repeat(3) { attempt ->
            coroutineContext.ensureActive()
            val url = search(patched, primaryR18)
            if (url.isNotEmpty() && url != avoidUrl) return url
            if (attempt < 2) delay(220)
        }
        return ""
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

    private suspend fun searchSliderOnly(ctx: Context, primaryR18: Int, recentUrls: Set<String>): String? {
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
                    )
                if (accept(url, recentUrls, preferFresh = false)) return url
                delay(18)
            }
        }
        return null
    }

    private suspend fun fallbackUntagged(ctx: Context, primaryR18: Int): String {
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
                        )
                    if (url.isNotEmpty()) return url
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
                )
            if (bare.isNotEmpty()) return bare
            repeat(2) {
                val url = SetuFetchPipeline.fetchBare(r18)
                if (url.isNotEmpty()) return url
            }
        }
        return ""
    }

    private fun accept(url: String, recentUrls: Set<String>, preferFresh: Boolean): Boolean {
        if (url.isEmpty()) return false
        if (!preferFresh) return true
        return url !in recentUrls
    }
}
