package com.kuroshimira.nyanpasu.search

/** 搜索意图 blob 与 Niche 打分，Lolicon / Danbooru 共用。 */
internal object SearchScoring {

    enum class StyleBand { SMALL, NEUTRAL, LARGE }

    fun intentBlob(strictTags: Array<String>, variant: List<String>): String =
        (strictTags.toList() + variant).joinToString("|").lowercase()

    fun scoreTagsForNiches(tagRaw: String, intentBlobLower: String): Int {
        val tagLower = tagRaw.lowercase()
        var score = 0
        for (spec in SearchNicheRegistry.specs) {
            if (!spec.matchesIntentBlob(intentBlobLower)) continue
            val pos =
                spec.loliconPositive.any { marker ->
                    marker in tagRaw || marker.lowercase() in tagLower
                }
            if (pos) score += 4
            if (spec.loliconPositive.isNotEmpty() &&
                spec.loliconDrift.isNotEmpty() &&
                !pos &&
                spec.loliconDrift.any { it in tagLower }
            ) {
                score -= 3
            }
        }
        return score
    }

    fun styleBand(styleValue: Int): StyleBand =
        when {
            styleValue < 35 -> StyleBand.SMALL
            styleValue > 65 -> StyleBand.LARGE
            else -> StyleBand.NEUTRAL
        }

    fun sliderTagGroup(band: StyleBand, relaxed: Boolean): String? =
        when (band) {
            StyleBand.SMALL ->
                if (relaxed) "small_breasts|flat_chest|petite|medium_breasts"
                else "small_breasts|flat_chest|petite"
            StyleBand.LARGE ->
                if (relaxed) "huge_breasts|large_breasts|gigantic_breasts|medium_breasts"
                else "huge_breasts|large_breasts|gigantic_breasts"
            StyleBand.NEUTRAL -> null
        }
}
