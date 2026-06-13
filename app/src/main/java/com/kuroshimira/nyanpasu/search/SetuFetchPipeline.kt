package com.kuroshimira.nyanpasu.search

/** Lolicon → Danbooru → Booru 池的串联编排。 */
internal object SetuFetchPipeline {

    suspend fun buildAndFetch(
        primaryTags: List<String>,
        softTags: Array<String>,
        styleValue: Int,
        r18Mode: Int,
        useSlider: Boolean,
        sliderRelaxed: Boolean,
        intentSourceTags: Array<String>,
        attachSoftTag: Boolean = false,
    ): String {
        val loliconUrl =
            LoliconSearchClient.fetchTagged(
                primaryTags,
                softTags,
                styleValue,
                r18Mode,
                useSlider,
                sliderRelaxed,
                intentSourceTags,
                attachSoftTag,
            )
        if (loliconUrl.isNotEmpty()) return loliconUrl

        val tagged = primaryTags.isNotEmpty()
        var dan =
            DanbooruSearchClient.fetchPixiv(
                primaryTags,
                softTags,
                styleValue,
                r18Mode,
                useSlider,
                sliderRelaxed,
                intentSourceTags,
                requirePixivId = true,
                compactTags = false,
            )
        if (dan.isEmpty() && tagged) {
            dan = DanbooruSearchClient.fetchBooruPool(primaryTags, intentSourceTags, r18Mode)
        }
        if (dan.isEmpty()) {
            dan =
                DanbooruSearchClient.fetchPixiv(
                    primaryTags,
                    softTags,
                    styleValue,
                    r18Mode,
                    useSlider,
                    sliderRelaxed,
                    intentSourceTags,
                    requirePixivId = false,
                    compactTags = true,
                )
        }
        if (dan.isEmpty()) {
            dan = DanbooruSearchClient.fetchBooruPool(primaryTags, intentSourceTags, r18Mode)
        }
        return dan
    }

    suspend fun fetchBare(r18Mode: Int): String {
        var url = LoliconSearchClient.fetchBare(r18Mode)
        if (url.isEmpty()) {
            url = DanbooruSearchClient.fetchBooruPool(emptyList(), emptyArray(), r18Mode)
        }
        return url
    }
}
