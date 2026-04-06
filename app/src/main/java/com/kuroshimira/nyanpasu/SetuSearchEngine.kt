package com.kuroshimira.nyanpasu

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * 图源链路（免 OAuth）：**Lolicon /setu/v2（Pixiv）→ Danbooru posts.json → 极简 Danbooru → Moebooru → Safebooru**。
 * **R18 策略**见 [R18Policy]：PURE(0) 下不得抬高 Lolicon 的 r18 参数，并对返回条目的 `r18` 字段二次过滤。
 */
internal object SetuSearchEngine {

    /**
     * 与 MainActivity 一致：`0=PURE`，`1=NSFW`，`2=MIX`。
     * 此前 PURE 在「Tier 2 / 保底链」里会改用 `r18=1|2`，导致全年龄模式仍拉到 R18。
     */
    private object R18Policy {
        /** Tier 2：在保留用户 Tag 的前提下，是否允许换用更宽的 Lolicon r18 参数。PURE 不允许。 */
        fun tierEscalations(appMode: Int): List<Int> =
            when (appMode) {
                0 -> emptyList()
                1 -> listOf(2)
                else -> listOf(1, 0)
            }

        /** 无 Tag / 仅滑条等保底：每种模式各自的 Lolicon r18 尝试顺序（不包含违约抬高）。 */
        fun fallbackLoliconChain(appMode: Int): List<Int> =
            when (appMode) {
                0 -> listOf(0)
                1 -> listOf(1, 2)
                else -> listOf(2, 1, 0)
            }

        fun enforceLoliconNonR18Items(appMode: Int): Boolean = appMode == 0
    }

    private const val BASE_URL = "https://api.lolicon.app/setu/v2"
    private const val DANBOORU_POSTS_JSON = "https://danbooru.donmai.us/posts.json"
    private const val SAFEBOORU_INDEX = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1"
    /** Moebooru 系：高质量壁纸比例多，与 Lolicon 互补。 */
    private const val MOE_KONACHAN_SAFE = "https://konachan.net/post.json"
    private const val MOE_KONACHAN_R18 = "https://konachan.com/post.json"
    private const val MOE_YANDE = "https://yande.re/post.json"
    private const val LOLICON_NUM = 16
    private const val TAG = "SetuSearch"

    /**
     * 用户词汇 / 检索变体里出现即激活该意图：扩展 OR 组 + Lolicon/Danbooru 打分 + Danbooru 额外 ~ 词。
     * 顺序越前优先级越高（互斥口味时先匹配靠前的）。
     */
    private data class NicheSpec(
        val id: String,
        val intentMarkers: List<String>,
        val orGroup: String,
        val fallbackSingles: List<String> = emptyList(),
        val lonelyExtras: List<String> = emptyList(),
        val loliconPositive: Array<String> = emptyArray(),
        val loliconDrift: Array<String> = emptyArray(),
        /** 与胸型滑条语义冲突（男性向、伪娘等） */
        val conflictsSlider: Boolean = false,
        val danbooruBoostOrs: List<String> = emptyList(),
    ) {
        /**
         * ASCII 且很短的标记只作「整词 tag」匹配，避免 `bl` 命中 `black`、`gl` 命中 `glow` 等。
         */
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

    private val nicheSpecs: List<NicheSpec> =
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

    private fun matchNicheForUser(norm: List<String>): NicheSpec? {
        for (spec in nicheSpecs) {
            if (spec.matchesUserTags(norm)) return spec
        }
        return null
    }

    private fun intentBlob(strictTags: Array<String>, variant: List<String>): String =
        (strictTags.toList() + variant).joinToString("|").lowercase()

    /** Pixiv/Lolicon 常返回日文 tag，仅用 lowercase 英文会全部判负分，导致「一直用不上图」。 */
    private fun scoreTagsForNiches(tagRaw: String, intentBlobLower: String): Int {
        val tagLower = tagRaw.lowercase()
        var score = 0
        for (spec in nicheSpecs) {
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

    private fun pickFromScoredDanbooru(
        scored: List<Pair<String, Int>>,
        intentBlobLower: String,
    ): String {
        if (scored.isEmpty()) return ""
        val nicheApplied = nicheSpecs.any { it.matchesIntentBlob(intentBlobLower) }
        if (nicheApplied) {
            val strict = scored.filter { it.second >= 2 }
            if (strict.isNotEmpty()) return strict[Random.nextInt(strict.size)].first
            val soft = scored.filter { it.second >= 1 }
            if (soft.isNotEmpty()) {
                Log.d(TAG, "danbooru niche soft-pick (pixiv/score tier)")
                return soft[Random.nextInt(soft.size)].first
            }
            val pool = scored.filter { it.second > 0 }.ifEmpty { scored }
            Log.w(TAG, "danbooru niche last-resort pick")
            return pool[Random.nextInt(pool.size)].first
        }
        val pool = scored.filter { it.second > 0 }.ifEmpty { scored }
        return pool[Random.nextInt(pool.size)].first
    }

    data class Context(
        val styleValue: Int,
        val strictTags: Array<String>,
        val softTags: Array<String>,
        val lastFetchedUrl: String,
    )

    private enum class StyleBand { SMALL, NEUTRAL, LARGE }

    private enum class SliderMode {
        /** 不按滑动条加体型 tag */
        OFF,

        /** 左/右偏好：紧组合 OR 组 */
        STRICT,

        /** 左/右偏好：并入 medium 等扩大命中 */
        RELAXED
    }

    private data class FetchProfile(val slider: SliderMode) {
        val useSlider: Boolean get() = slider != SliderMode.OFF
        val sliderRelaxed: Boolean get() = slider == SliderMode.RELAXED
    }

    suspend fun search(ctx: Context, primaryR18: Int): String {
        val last = ctx.lastFetchedUrl
        if (ctx.strictTags.isEmpty()) {
            return fallbackUntagged(ctx, primaryR18)
        }

        val variants = TagExpander.expand(ctx.strictTags)
        val sliderAllowed = !TagExpander.isConflictWithSlider(ctx.strictTags)
        val profileLadder = buildProfileLadder(sliderAllowed)

        // Tier 1 — 严格遵循当前模式的 Lolicon r18（0/1/2），Tag 变体 × 体型阶梯
        firstNonEmpty(ctx, primaryR18, variants, profileLadder, last, preferFresh = true, attemptsPerCell = 3)
            ?.let { return it }

        // Tier 2 — NSFW/MIX 下可酌情抬高 Lolicon r18；PURE 绝不抬高
        for (r18 in R18Policy.tierEscalations(primaryR18)) {
            firstNonEmpty(ctx, r18, variants, profileLadder, last, preferFresh = true, attemptsPerCell = 2)
                ?.let { return it }
        }

        // Tier 3 — 放弃用户 Tag，保留滑动条体型（链内 r18 仍遵守 R18Policy）
        searchSliderOnly(ctx, primaryR18, last)?.let { return it }

        // Tier 4 — 无 Tag 保底
        return fallbackUntagged(ctx, primaryR18)
    }

    /**
     * 独立锁屏第二张图：在「与主屏 URL 不同」上多试几轮，仍走同一套分层降级。
     */
    suspend fun searchDistinct(
        ctx: Context,
        primaryR18: Int,
        avoidUrl: String,
    ): String {
        val patched = ctx.copy(lastFetchedUrl = avoidUrl)
        repeat(3) { attempt ->
            val url = search(patched, primaryR18)
            if (url.isNotEmpty() && url != avoidUrl) return url
            if (attempt < 2) delay(220)
        }
        return ""
    }

    private fun buildProfileLadder(sliderAllowed: Boolean): List<FetchProfile> = buildList {
        if (sliderAllowed) {
            add(FetchProfile(SliderMode.STRICT))
            add(FetchProfile(SliderMode.OFF))
            add(FetchProfile(SliderMode.RELAXED))
        } else {
            add(FetchProfile(SliderMode.OFF))
        }
    }

    private suspend fun firstNonEmpty(
        ctx: Context,
        r18: Int,
        variants: List<List<String>>,
        profiles: List<FetchProfile>,
        last: String,
        preferFresh: Boolean,
        attemptsPerCell: Int,
    ): String? {
        for (variant in variants) {
            for (profile in profiles) {
                repeat(attemptsPerCell) {
                    val url = buildAndFetch(
                        variant,
                        ctx.softTags,
                        ctx.styleValue,
                        r18,
                        profile.useSlider,
                        profile.sliderRelaxed,
                        ctx.strictTags,
                    )
                    if (accept(url, last, preferFresh)) {
                        Log.d(TAG, "hit r18=$r18 variant=$variant profile=$profile")
                        return url
                    }
                    delay(80)
                }
            }
        }
        return null
    }

    private suspend fun searchSliderOnly(ctx: Context, primaryR18: Int, last: String): String? {
        val chain = R18Policy.fallbackLoliconChain(primaryR18)
        for (r18 in chain) {
            for (relaxed in listOf(false, true)) {
                repeat(6) {
                    val url = buildAndFetch(
                        emptyList(),
                        ctx.softTags,
                        ctx.styleValue,
                        r18,
                        useSlider = true,
                        sliderRelaxed = relaxed,
                        ctx.strictTags,
                    )
                    if (accept(url, last, preferFresh = true)) return url
                    delay(45)
                }
            }
            for (relaxed in listOf(false, true)) {
                repeat(3) {
                    val url = buildAndFetch(
                        emptyList(),
                        ctx.softTags,
                        ctx.styleValue,
                        r18,
                        useSlider = true,
                        sliderRelaxed = relaxed,
                        ctx.strictTags,
                    )
                    if (url.isNotEmpty()) return url
                    delay(40)
                }
            }
        }
        return null
    }

    private suspend fun fallbackUntagged(ctx: Context, primaryR18: Int): String {
        val chain = R18Policy.fallbackLoliconChain(primaryR18)
        for (r18 in chain) {
            for (relaxed in listOf(false, true)) {
                repeat(5) {
                    val url = buildAndFetch(
                        emptyList(),
                        ctx.softTags,
                        ctx.styleValue,
                        r18,
                        useSlider = true,
                        sliderRelaxed = relaxed,
                        ctx.strictTags,
                    )
                    if (url.isNotEmpty()) return url
                    delay(40)
                }
            }
            repeat(3) {
                val url = buildAndFetch(
                    emptyList(),
                    ctx.softTags,
                    ctx.styleValue,
                    r18,
                    useSlider = false,
                    sliderRelaxed = false,
                    ctx.strictTags,
                )
                if (url.isNotEmpty()) return url
                delay(40)
            }
            repeat(2) {
                val url = fetchBare(r18)
                if (url.isNotEmpty()) return url
            }
        }
        return ""
    }

    private fun accept(url: String, last: String, preferFresh: Boolean): Boolean {
        if (url.isEmpty()) return false
        if (!preferFresh) return true
        return url != last
    }

    private fun styleBand(styleValue: Int): StyleBand = when {
        styleValue < 35 -> StyleBand.SMALL
        styleValue > 65 -> StyleBand.LARGE
        else -> StyleBand.NEUTRAL
    }

    /**
     * Lolicon 最多 3 组 AND。体型占 1 组时主 tag 最多 2 组；无体型时主 tag 最多 3 组（避免伪娘+双作品被错误 OR 合并）。
     */
    private fun loliconPrimaryTagSlots(
        primaryTags: List<String>,
        useSlider: Boolean,
        band: StyleBand,
        sliderRelaxed: Boolean,
    ): List<String> {
        val mapped = primaryTags.map { it.trim() }.filter { it.isNotEmpty() }
        if (mapped.isEmpty()) return emptyList()
        val sliderOccupiesSlot =
            useSlider && sliderTagGroup(band, sliderRelaxed) != null
        val maxPrimary = if (sliderOccupiesSlot) 2 else 3
        return mapped.take(maxPrimary)
    }

    private fun sliderTagGroup(band: StyleBand, relaxed: Boolean): String? {
        return when (band) {
            StyleBand.SMALL ->
                if (relaxed) "small_breasts|flat_chest|petite|medium_breasts"
                else "small_breasts|flat_chest|petite"
            StyleBand.LARGE ->
                if (relaxed) "huge_breasts|large_breasts|gigantic_breasts|medium_breasts"
                else "huge_breasts|large_breasts|gigantic_breasts"
            StyleBand.NEUTRAL -> null
        }
    }

    private fun buildLoliconPath(
        primaryTags: List<String>,
        softTags: Array<String>,
        styleValue: Int,
        r18Mode: Int,
        useSlider: Boolean,
        sliderRelaxed: Boolean,
        withAspectRatio: Boolean,
        withExcludeAi: Boolean,
    ): String {
        val params = StringBuilder("?r18=$r18Mode&size=regular")
        val band = styleBand(styleValue)

        val primarySlots = loliconPrimaryTagSlots(primaryTags, useSlider, band, sliderRelaxed)
        primarySlots.forEach { params.append("&tag=${encode(it)}") }

        if (useSlider) {
            val group = sliderTagGroup(band, sliderRelaxed)
            if (group != null) {
                params.append("&tag=${encode(group)}")
            }
        }

        if (primarySlots.isEmpty() && softTags.isNotEmpty() && Random.nextInt(100) < 38) {
            params.append("&tag=${encode(softTags.random())}")
        }

        if (withExcludeAi) params.append("&excludeAI=true")
        if (withAspectRatio) params.append("&aspectRatio=lt1")
        params.append("&num=$LOLICON_NUM")
        return params.toString()
    }

    private fun buildAndFetch(
        primaryTags: List<String>,
        softTags: Array<String>,
        styleValue: Int,
        r18Mode: Int,
        useSlider: Boolean,
        sliderRelaxed: Boolean,
        intentSourceTags: Array<String>,
    ): String {
        val intentBlobLower = intentBlob(intentSourceTags, primaryTags)
        val dropR18Items = R18Policy.enforceLoliconNonR18Items(r18Mode)

        fun loliconTry(aspect: Boolean, excludeAi: Boolean): String =
            fetchLoliconJson(
                BASE_URL +
                    buildLoliconPath(
                        primaryTags,
                        softTags,
                        styleValue,
                        r18Mode,
                        useSlider,
                        sliderRelaxed,
                        withAspectRatio = aspect,
                        withExcludeAi = excludeAi,
                    ),
                intentBlobLower,
                dropMarkedR18 = dropR18Items,
            )

        var loliconUrl = loliconTry(aspect = true, excludeAi = true)
        if (loliconUrl.isEmpty()) {
            loliconUrl = loliconTry(aspect = false, excludeAi = true)
        }
        if (loliconUrl.isEmpty()) {
            loliconUrl = loliconTry(aspect = false, excludeAi = false)
        }
        if (loliconUrl.isNotEmpty()) return loliconUrl

        var dan = fetchDanbooruPixiv(
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
        if (dan.isEmpty()) {
            dan = fetchDanbooruPixiv(
                primaryTags,
                softTags,
                styleValue,
                r18Mode,
                useSlider,
                sliderRelaxed,
                intentSourceTags,
                requirePixivId = false,
                compactTags = false,
            )
        }
        if (dan.isEmpty()) {
            dan = fetchDanbooruPixiv(
                primaryTags,
                softTags,
                styleValue,
                r18Mode,
                useSlider,
                sliderRelaxed,
                intentSourceTags,
                requirePixivId = true,
                compactTags = true,
            )
        }
        if (dan.isEmpty()) {
            dan = fetchDanbooruPixiv(
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
            dan = fetchMoebooruPosts(primaryTags, intentSourceTags, r18Mode)
        }
        if (dan.isEmpty()) {
            dan = fetchSafebooruLastResort(primaryTags, intentSourceTags, r18Mode)
        }
        return dan
    }

    private fun fetchBare(r18Mode: Int): String {
        val path =
            buildLoliconPath(
                primaryTags = emptyList(),
                softTags = emptyArray(),
                styleValue = 50,
                r18Mode = r18Mode,
                useSlider = false,
                sliderRelaxed = false,
                withAspectRatio = false,
                withExcludeAi = true,
            )
        return fetchLoliconJson(
            BASE_URL + path,
            intentBlobLower = "",
            dropMarkedR18 = R18Policy.enforceLoliconNonR18Items(r18Mode),
        )
    }

    private fun encode(str: String): String =
        try {
            URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }

    private fun fetchLoliconJson(
        urlString: String,
        intentBlobLower: String,
        dropMarkedR18: Boolean,
    ): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6500
                readTimeout = 6500
                setRequestProperty("User-Agent", "Nyanpasu/1.0 (Pixiv wallpaper; +Android)")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return ""
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            val dataArray = jsonObject.optJSONArray("data") ?: return ""
            pickLoliconRegularUrl(dataArray, intentBlobLower, dropMarkedR18)
        } catch (e: Exception) {
            Log.e(TAG, "lolicon: ${e.message}")
            ""
        } finally {
            conn?.disconnect()
        }
    }

    private fun pickLoliconRegularUrl(
        dataArray: JSONArray,
        intentBlobLower: String,
        dropMarkedR18: Boolean,
    ): String {
        if (dataArray.length() == 0) return ""
        val entries = ArrayList<Pair<JSONObject, Int>>(dataArray.length())
        val relevance = nicheSpecs.any { it.matchesIntentBlob(intentBlobLower) }
        for (i in 0 until dataArray.length()) {
            val o = dataArray.optJSONObject(i) ?: continue
            if (dropMarkedR18 && o.optBoolean("r18", false)) continue
            val urls = o.optJSONObject("urls") ?: continue
            val reg = urls.optString("regular", "")
            if (reg.isEmpty()) continue
            val tagStr = joinLoliconTags(o.optJSONArray("tags"))
            val score = scoreTagsForNiches(tagStr, intentBlobLower)
            entries.add(o to score)
        }
        if (entries.isEmpty()) return ""
        if (relevance) {
            val good = entries.filter { it.second >= 1 }
            if (good.isNotEmpty()) {
                val chosen = good[Random.nextInt(good.size)].first
                return chosen.getJSONObject("urls").getString("regular")
            }
            val soft = entries.filter { it.second >= 0 }
            if (soft.isNotEmpty()) {
                Log.d(TAG, "lolicon niche soft-pick (API 已按 tag 过滤，放宽打分)")
                val chosen = soft[Random.nextInt(soft.size)].first
                return chosen.getJSONObject("urls").getString("regular")
            }
            val chosen = entries[Random.nextInt(entries.size)].first
            Log.w(TAG, "lolicon niche last-resort random")
            return chosen.getJSONObject("urls").getString("regular")
        }
        val chosen = entries[Random.nextInt(entries.size)].first
        return chosen.getJSONObject("urls").getString("regular")
    }

    private fun joinLoliconTags(tags: JSONArray?): String {
        if (tags == null || tags.length() == 0) return ""
        return buildString {
            for (i in 0 until tags.length()) {
                if (i > 0) append(' ')
                append(tags.optString(i))
            }
        }
    }

    private fun fetchDanbooruPixiv(
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
        val intentBlobLower = intentBlob(intentSourceTags, primaryTags)
        val parts = ArrayList<String>()
        if (compactTags) {
            danbooruRatingClause(r18Mode)?.let { parts.add(it) }
            if (requirePixivId) parts.add("pixiv_id:>0")
            parts.add("order:random")
            val raw =
                primaryTags.firstOrNull()?.trim()?.let { tok ->
                    if ("|" in tok) tok.substringBefore("|").trim() else tok
                }?.replace(' ', '_')?.lowercase()
            if (!raw.isNullOrEmpty()) parts.add(raw)
        } else {
            danbooruRatingClause(r18Mode)?.let { parts.add(it) }
            if (requirePixivId) parts.add("pixiv_id:>0")
            parts.add("order:random")
            primaryTags.forEach { parts.addAll(loliconTokenToDanbooruTerms(it)) }
            if (useSlider) {
                val band = styleBand(styleValue)
                danbooruBodySlideTerms(band, sliderRelaxed)?.let { parts.add(it) }
            }
            if (primaryTags.isEmpty() && softTags.isNotEmpty() && Random.nextInt(100) < 40) {
                parts.addAll(loliconTokenToDanbooruTerms(softTags.random()))
            }
            for (spec in nicheSpecs) {
                if (spec.matchesIntentBlob(intentBlobLower)) {
                    spec.danbooruBoostOrs.forEach { parts.add(it) }
                }
            }
        }
        val tagQuery = parts.filter { it.isNotBlank() }.joinToString(" ")
        if (tagQuery.length > 1500) return ""
        val url =
            "$DANBOORU_POSTS_JSON?limit=25&tags=${encode(tagQuery)}"
        return fetchDanbooruInternal(url, intentBlobLower, requirePixivId, r18Mode)
    }

    /**
     * Konachan / yande.re：Moebooru `post.json`，免 Key；手机屏幕优先 sample_url / jpeg_url。
     */
    private fun fetchMoebooruPosts(
        primaryTags: List<String>,
        intentSourceTags: Array<String>,
        r18Mode: Int,
    ): String {
        val endpoint =
            when (r18Mode) {
                0 -> MOE_KONACHAN_SAFE
                1 -> MOE_KONACHAN_R18
                else -> MOE_YANDE
            }
        val ratingExtra =
            when (r18Mode) {
                0 -> "rating:safe"
                1 -> "rating:explicit"
                else -> null
            }
        val blob = intentBlob(intentSourceTags, primaryTags)
        val niche = nicheSpecs.firstOrNull { it.matchesIntentBlob(blob) }
        val tagCandidates = LinkedHashSet<String>()
        if (primaryTags.isNotEmpty()) {
            val p = primaryTags.first().trim()
            val one = (if ("|" in p) p.substringBefore("|") else p).replace(' ', '_').lowercase()
            if (one.isNotEmpty()) tagCandidates.add(one)
        }
        niche?.fallbackSingles?.forEach {
            val t = it.trim().replace(' ', '_').lowercase()
            if (t.isNotEmpty()) tagCandidates.add(t)
        }
        if (tagCandidates.isEmpty()) tagCandidates.add("1girl")

        val ua =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Nyanpasu/1.0"
        for (tagBase in tagCandidates) {
            val tags =
                if (ratingExtra != null) {
                    "$tagBase $ratingExtra"
                } else {
                    tagBase
                }
            var conn: HttpURLConnection? = null
            try {
                val urlStr = "$endpoint?limit=20&tags=${encode(tags.trim())}"
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7500
                    readTimeout = 7500
                    setRequestProperty("User-Agent", ua)
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) continue
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(response)
                if (arr.length() == 0) continue
                val urls = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val rt = o.optString("rating", "").lowercase()
                    when (r18Mode) {
                        0 -> if (rt != "safe") continue
                        1 -> if (rt == "safe") continue
                        else -> {}
                    }
                    val sample = o.optString("sample_url", "")
                    val jpeg = o.optString("jpeg_url", "")
                    val file = o.optString("file_url", "")
                    val pick =
                        when {
                            sample.isNotEmpty() -> sample
                            jpeg.isNotEmpty() -> jpeg
                            file.isNotEmpty() -> file
                            else -> ""
                        }
                    if (pick.isNotEmpty()) urls.add(pick)
                }
                if (urls.isNotEmpty()) {
                    Log.d(TAG, "moebooru hit tags=$tags host=$endpoint")
                    return urls[Random.nextInt(urls.size)]
                }
            } catch (e: Exception) {
                Log.e(TAG, "moebooru: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return ""
    }

    /**
     * Gelbooru 系 Safebooru，仅全年龄；在 Lolicon+Danbooru 都空且用户为 PURE 时兜底，避免无限重试。
     */
    private fun fetchSafebooruLastResort(
        primaryTags: List<String>,
        intentSourceTags: Array<String>,
        r18Mode: Int,
    ): String {
        if (r18Mode != 0) return ""
        val blob = intentBlob(intentSourceTags, primaryTags)
        val niche = nicheSpecs.firstOrNull { it.matchesIntentBlob(blob) } ?: return ""
        val tryTags = mutableListOf<String>()
        tryTags.addAll(niche.fallbackSingles)
        if (niche.orGroup.isNotEmpty()) tryTags.add(niche.orGroup.split("|").first())
        var conn: HttpURLConnection? = null
        for (t in tryTags.distinct()) {
            val tag = t.trim().replace(' ', '_').lowercase()
            if (tag.isEmpty()) continue
            try {
                val urlStr = "$SAFEBOORU_INDEX&tags=${encode(tag)}&limit=20"
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Nyanpasu/1.0 (Pixiv wallpaper; +Android)")
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    conn = null
                    continue
                }
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                conn = null
                val arr = JSONArray(response)
                if (arr.length() == 0) continue
                val urls = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val file = o.optString("file_url", "")
                    if (file.isNotEmpty()) urls.add(file)
                }
                if (urls.isNotEmpty()) {
                    Log.d(TAG, "safebooru hit tag=$tag")
                    return urls[Random.nextInt(urls.size)]
                }
            } catch (e: Exception) {
                Log.e(TAG, "safebooru: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return ""
    }

    private fun danbooruRatingClause(r18Mode: Int): String? =
        when (r18Mode) {
            0 -> "rating:general"
            1 -> "-rating:general"
            else -> null
        }

    private fun loliconTokenToDanbooruTerms(token: String): List<String> {
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

    private fun danbooruBodySlideTerms(band: StyleBand, relaxed: Boolean): String? =
        when (band) {
            StyleBand.SMALL ->
                if (relaxed) "~small_breasts ~flat_chest ~petite ~medium_breasts"
                else "~small_breasts ~flat_chest ~petite"
            StyleBand.LARGE ->
                if (relaxed) "~huge_breasts ~large_breasts ~gigantic_breasts ~medium_breasts"
                else "~huge_breasts ~large_breasts ~gigantic_breasts"
            StyleBand.NEUTRAL -> null
        }

    private fun fetchDanbooruInternal(
        urlString: String,
        intentBlobLower: String,
        requirePixivId: Boolean,
        appR18Mode: Int,
    ): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Nyanpasu/1.0 (Pixiv wallpaper; +Android)")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return ""
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(response)
            if (arr.length() == 0) return ""
            val scored = ArrayList<Pair<String, Int>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("is_deleted")) continue
                if (appR18Mode == 0) {
                    val ratingChar = o.optString("rating", "")
                    if (ratingChar != "g") continue
                }
                val pix = o.optLong("pixiv_id", 0L)
                if (requirePixivId && pix <= 0L) continue
                val fileUrl = o.optString("file_url", "")
                if (fileUrl.isEmpty()) continue
                val tagRaw = o.optString("tag_string", "")
                var sc = 1 + scoreTagsForNiches(tagRaw, intentBlobLower)
                if (!requirePixivId && pix > 0L) sc += 1
                scored.add(fileUrl to sc)
            }
            if (scored.isEmpty()) return ""
            pickFromScoredDanbooru(scored, intentBlobLower)
        } catch (e: Exception) {
            Log.e(TAG, "danbooru: ${e.message}")
            ""
        } finally {
            conn?.disconnect()
        }
    }

    object TagExpander {

        fun expand(userTags: Array<String>): List<List<String>> {
            if (userTags.isEmpty()) return listOf(emptyList())

            val norm =
                userTags
                    .map { it.trim().lowercase().removePrefix("#") }
                    .filter { it.isNotEmpty() }
            val niche = matchNicheForUser(norm)
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
                "蔚蓝档案", "ba" -> "Blue_Archive"
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
            if (nicheSpecs.any { it.conflictsSlider && it.matchesUserTags(norm) }) {
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
}
