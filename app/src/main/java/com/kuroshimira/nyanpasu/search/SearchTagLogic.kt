package com.kuroshimira.nyanpasu.search

/**
 * Tag 扩展、小众口味（Niche）注册表；从 [SetuSearchEngine] 拆出以降低单文件复杂度。
 */
internal object SearchNicheRegistry {

    data class NicheSpec(
        val id: String,
        val intentMarkers: List<String>,
        val orGroup: String,
        val fallbackSingles: List<String> = emptyList(),
        val lonelyExtras: List<String> = emptyList(),
        val loliconPositive: Array<String> = emptyArray(),
        val loliconDrift: Array<String> = emptyArray(),
        val conflictsSlider: Boolean = false,
        val danbooruBoostOrs: List<String> = emptyList(),
    ) {
        fun matchesUserTags(norm: List<String>): Boolean {
            val blob = norm.joinToString("|")
            return intentMarkers.any { marker ->
                val m = marker.lowercase()
                val asciiShort =
                    m.isNotEmpty() && m.all { it in 'a'..'z' || it in '0'..'9' } && m.length <= 3
                if (asciiShort) {
                    norm.any { it == m }
                } else {
                    m in blob || norm.any { it.contains(m) }
                }
            }
        }

        fun matchesIntentBlob(intentBlobLower: String): Boolean =
            intentMarkers.any { marker -> marker.lowercase() in intentBlobLower }
    }

    val specs: List<NicheSpec> =
        listOf(
            NicheSpec(
                id = "femboy",
                intentMarkers =
                    listOf(
                        "男娘",
                        "伪娘",
                        "femboy",
                        "otokonoko",
                        "crossdressing",
                        "josou",
                        "otoko_no_ko",
                    ),
                orGroup = "otokonoko|trap|otoko_no_ko|crossdressing|femboy|josou_seme|josou_uke",
                fallbackSingles = listOf("otokonoko", "trap", "crossdressing", "femboy", "otoko_no_ko"),
                lonelyExtras = listOf("josou_seme", "josou_uke"),
                loliconPositive =
                    arrayOf(
                        "otokonoko",
                        "trap",
                        "crossdressing",
                        "femboy",
                        "josou",
                        "otoko no ko",
                        "otoko_no_ko",
                        "male maiden",
                        "男の娘",
                        "女装",
                        "女裝",
                        "偽娘",
                    ),
                loliconDrift =
                    arrayOf(
                        "bara",
                        "muscular male",
                        "muscular_male",
                        "hairy male",
                        "yaoi",
                        "males only",
                        "males_only",
                    ),
                conflictsSlider = true,
                danbooruBoostOrs =
                    listOf(
                        "~otokonoko",
                        "~trap",
                        "~crossdressing",
                        "~otoko_no_ko",
                        "~femboy",
                    ),
            ),
            NicheSpec(
                id = "yuri",
                intentMarkers =
                    listOf(
                        "yuri",
                        "百合",
                        "gl",
                        "girls_love",
                        "girls love",
                        "shoujo_ai",
                        "shojo_ai",
                    ),
                orGroup = "yuri|girls_love|shoujo_ai",
                fallbackSingles = listOf("yuri", "girls_love", "shoujo_ai"),
                loliconPositive = arrayOf("yuri", "girls_love", "girls love", "shoujo_ai", "shojo_ai"),
                loliconDrift = arrayOf("yaoi", "bara", "males_only", "males only"),
                danbooruBoostOrs = listOf("~yuri", "~girls_love", "~shoujo_ai"),
            ),
            NicheSpec(
                id = "yaoi",
                intentMarkers =
                    listOf(
                        "yaoi",
                        "耽美",
                        "bl",
                        "boys_love",
                        "boys love",
                        "shounen_ai",
                    ),
                orGroup = "yaoi|boys_love|shounen_ai",
                fallbackSingles = listOf("yaoi", "boys_love", "shounen_ai"),
                loliconPositive = arrayOf("yaoi", "boys_love", "boys love", "shounen_ai", "shounen-ai"),
                loliconDrift = arrayOf("yuri", "girls_love", "girls love", "shoujo_ai"),
                conflictsSlider = true,
                danbooruBoostOrs = listOf("~yaoi", "~boys_love", "~shounen_ai"),
            ),
            NicheSpec(
                id = "furry",
                intentMarkers =
                    listOf(
                        "furry",
                        "kemono",
                        "兽人",
                        "ケモノ",
                        "福瑞",
                    ),
                orGroup = "furry|kemono|animal_ears",
                fallbackSingles = listOf("furry", "kemono", "animal_ears"),
                loliconPositive = arrayOf("furry", "kemono", "animal ears", "animal_ears"),
                loliconDrift = arrayOf(),
                danbooruBoostOrs = listOf("~furry", "~kemono"),
            ),
            NicheSpec(
                id = "feet",
                intentMarkers = listOf("足控", "foot_focus", "feet", "裸足", "足", "footjob"),
                orGroup = "foot_focus|feet|barefoot|soles",
                fallbackSingles = listOf("foot_focus", "feet", "barefoot"),
                loliconPositive = arrayOf("foot focus", "foot_focus", "feet", "barefoot", "soles", "toes"),
                loliconDrift = arrayOf(),
                danbooruBoostOrs = listOf("~foot_focus", "~feet", "~barefoot"),
            ),
            NicheSpec(
                id = "bondage",
                intentMarkers = listOf("捆绑", "bdsm", "bondage", "束缚", "绳"),
                orGroup = "bondage|bdsm|shibari|rope",
                fallbackSingles = listOf("bondage", "bdsm", "shibari"),
                loliconPositive = arrayOf("bondage", "bdsm", "shibari", "rope"),
                loliconDrift = arrayOf(),
                danbooruBoostOrs = listOf("~bondage", "~bdsm", "~shibari"),
            ),
            NicheSpec(
                id = "ntr",
                intentMarkers = listOf("ntr", "netorare", "寝取", "牛头人"),
                orGroup = "netorare|ntr|cheating",
                fallbackSingles = listOf("netorare", "ntr", "cheating"),
                loliconPositive = arrayOf("netorare", "ntr", "cheating"),
                loliconDrift = arrayOf(),
                danbooruBoostOrs = listOf("~netorare", "~ntr", "~cheating"),
            ),
            NicheSpec(
                id = "tentacle",
                intentMarkers = listOf("触手", "tentacle", "tentacles"),
                orGroup = "tentacles|tentacle_pit|tentacle",
                fallbackSingles = listOf("tentacles", "tentacle"),
                loliconPositive = arrayOf("tentacle", "tentacles"),
                loliconDrift = arrayOf(),
                danbooruBoostOrs = listOf("~tentacles", "~tentacle"),
            ),
            NicheSpec(
                id = "mesugaki",
                intentMarkers = listOf("雌小鬼", "mesugaki", "小恶魔", "smug"),
                orGroup = "mesugaki|smug|brat",
                fallbackSingles = listOf("mesugaki", "smug"),
                loliconPositive = arrayOf("mesugaki", "smug"),
                loliconDrift = arrayOf(),
                danbooruBoostOrs = listOf("~mesugaki", "~smug"),
            ),
        )

    fun matchForUser(norm: List<String>): NicheSpec? {
        for (spec in specs) {
            if (spec.matchesUserTags(norm)) return spec
        }
        return null
    }
}

object TagExpander {

    fun expand(userTags: Array<String>): List<List<String>> {
        if (userTags.isEmpty()) return listOf(emptyList())

        val norm =
            userTags
                .map { it.trim().lowercase().removePrefix("#") }
                .filter { it.isNotEmpty() }
        val niche = SearchNicheRegistry.matchForUser(norm)
        if (niche != null) {
            val others =
                userTags
                    .filterNot { tag ->
                        val one = tag.trim().lowercase().removePrefix("#")
                        niche.matchesUserTags(listOf(one))
                    }
                    .map { mapSingle(it) }
                    .filter { it.isNotEmpty() }
            fun row(extra: List<String>): List<String> = others + extra
            val rows = mutableListOf<List<String>>()
            rows.add(row(listOf(niche.orGroup)))
            if (niche.id == "femboy") {
                rows.add(row(listOf("otokonoko", "male_focus")))
                rows.add(row(listOf("trap", "male_focus")))
            }
            niche.fallbackSingles.forEach { s -> rows.add(row(listOf(s))) }
            if (others.isEmpty()) {
                niche.lonelyExtras.forEach { e -> rows.add(listOf(e)) }
            }
            return rows
        }

        val result = mutableListOf<List<String>>()
        val lowerTags = norm

        val hasTouhou =
            lowerTags.any {
                it in listOf("东方", "touhou", "东方project") || "touhou" in it
            }
        val hasGenshin =
            lowerTags.any {
                it in listOf("原神", "genshin", "genshin impact") || "genshin" in it
            }
        val hasBlueArchive =
            lowerTags.any {
                it in listOf("蔚蓝档案", "blue archive") ||
                    "blue_archive" in it.replace(" ", "_") ||
                    it == "ba"
            }
        val hasStarRail =
            lowerTags.any {
                it in
                    listOf(
                        "星穹铁道",
                        "崩铁",
                        "honkai star rail",
                        "honkai_star_rail",
                        "スターレイル",
                    ) ||
                    "star_rail" in it
            }
        val hasZenless =
            lowerTags.any {
                it in listOf("绝区零", "zenless", "zzz", "ゼンゼロ") || "zenless" in it
            }
        val hasArknights =
            lowerTags.any {
                it in listOf("明日方舟", "arknights", "アークナイツ") || "arknights" in it
            }
        val hasFate =
            lowerTags.any {
                it in listOf("fate", "fgo", "命运冠位") || "fate/" in it
            }
        val hasHololive =
            lowerTags.any {
                it in listOf("hololive", "ホロライブ") || "hololive" in it
            }

        val franchiseHints =
            listOf(
                hasTouhou,
                hasGenshin,
                hasBlueArchive,
                hasStarRail,
                hasZenless,
                hasArknights,
                hasFate,
                hasHololive,
            ).count { it }
        if (franchiseHints >= 2) {
            result.add(userTags.map { mapSingle(it) }.filter { it.isNotEmpty() })
            return result
        }

        when {
            hasTouhou -> {
                result.add(listOf("Touhou"))
                result.add(listOf("touhou_project"))
                result.add(listOf("東方"))
            }
            hasGenshin -> {
                result.add(listOf("Genshin_Impact"))
                result.add(listOf("genshin"))
                result.add(listOf("原神"))
            }
            hasBlueArchive -> {
                result.add(listOf("Blue_Archive"))
                result.add(listOf("blue_archive"))
                result.add(listOf("ブルーアーカイブ"))
            }
            hasStarRail -> {
                result.add(listOf("Honkai:_Star-Rail"))
                result.add(listOf("Honkai_Star_Rail"))
            }
            hasZenless -> {
                result.add(listOf("Zenless_Zone_Zero"))
            }
            hasArknights -> {
                result.add(listOf("Arknights"))
            }
            hasFate -> {
                result.add(listOf("Fate"))
                result.add(listOf("Fate/Grand_Order"))
            }
            hasHololive -> {
                result.add(listOf("Hololive"))
            }
            else -> result.add(userTags.map { mapSingle(it) })
        }
        return result
    }

    private fun mapSingle(input: String): String {
        val lower = input.trim().lowercase()
        return when (lower) {
            "蔚蓝档案", "ba", "blue archive" -> "Blue_Archive"
            "原神", "genshin" -> "Genshin_Impact"
            "东方", "touhou" -> "Touhou"
            "白毛", "白发", "white hair" -> "white_hair"
            "黑丝", "black pantyhose", "裤袜" -> "black_pantyhose"
            "肉丝" -> "pantyhose"
            "兽耳", "猫耳", "nekomimi" -> "animal_ears"
            "百合" -> "yuri"
            "泳装", "水着" -> "swimsuit"
            "女仆", "maid" -> "maid"
            "和服", "着物" -> "kimono"
            "眼镜", "眼鏡" -> "glasses"
            "双马尾" -> "twintails"
            "单马尾" -> "ponytail"
            "巨乳" -> "large_breasts"
            "贫乳", "平胸" -> "small_breasts"
            "触手" -> "tentacles"
            "丝袜" -> "thighhighs"
            else -> input.trim()
        }
    }

    fun isConflictWithSlider(userTags: Array<String>): Boolean {
        val norm =
            userTags
                .map { it.trim().lowercase().removePrefix("#") }
                .filter { it.isNotEmpty() }
        if (SearchNicheRegistry.specs.any { it.conflictsSlider && it.matchesUserTags(norm) }) {
            return true
        }
        val conflictKeywords =
            listOf(
                "男娘",
                "伪娘",
                "femboy",
                "otokonoko",
                "trap",
                "crossdressing",
                "shota",
                "boy",
                "1boy",
                "males_only",
                "male_focus",
                "yaoi",
                "耽美",
                "bara",
            )
        return userTags.any { tag ->
            val t = tag.lowercase()
            conflictKeywords.any { kw -> kw in t }
        }
    }
}
