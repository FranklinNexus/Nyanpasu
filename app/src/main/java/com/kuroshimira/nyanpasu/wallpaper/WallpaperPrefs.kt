package com.kuroshimira.nyanpasu.wallpaper
import com.kuroshimira.nyanpasu.R

import android.content.Context
import android.content.SharedPreferences

/** 自动任务与 Worker 共用的偏好读写，避免 InputData 快照过期。 */
object WallpaperPrefs {

    const val PREFS_NAME = "ACG_PREFS"

    const val KEY_HOME_STATE = "HOME_STATE"
    const val KEY_LOCK_STATE = "LOCK_STATE"
    const val KEY_DAILY_ENABLED = "DAILY_ENABLED"
    const val KEY_SCHEDULE_INDEX = "SCHEDULE_INDEX"
    const val KEY_SAVED_TAGS = "SAVED_TAGS_V2"
    const val KEY_R18_MODE = "R18_MODE"
    const val KEY_STYLE = "STYLE"

    /** 默认最左（偏萌/保守），避免新用户首屏过多擦边内容。 */
    const val DEFAULT_STYLE = 0
    const val KEY_LAST_FETCHED_URL = "LAST_FETCHED_URL"
    const val KEY_RECENT_FETCHED_URLS = "RECENT_FETCHED_URLS"
    const val KEY_BUFFER_URL_A = "BUFFER_URL_A"
    const val KEY_BUFFER_URL_B = "BUFFER_URL_B"
    const val KEY_BUFFER_FP_A = "BUFFER_FP_A"
    const val KEY_BUFFER_FP_B = "BUFFER_FP_B"
    const val RECENT_URL_MAX = 8
    const val KEY_FIRST_LAUNCH = "IS_FIRST_LAUNCH"
    const val KEY_PENDING_AUTO_FAILURE = "PENDING_AUTO_FAILURE"

    /** Tag 名与 strict 标记分隔符（避免 tag 含 `|` 时解析错位）。 */
    const val TAG_ENTRY_SEP = "\u001F"

    const val SCHEDULE_COUNT = 4

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun coerceScheduleIndex(index: Int): Int =
        index.coerceIn(0, SCHEDULE_COUNT - 1)

    fun readScheduleIndex(prefs: SharedPreferences): Int =
        coerceScheduleIndex(prefs.getInt(KEY_SCHEDULE_INDEX, 0))

    fun readHomeLockState(prefs: SharedPreferences): Pair<Int, Int> =
        prefs.getInt(KEY_HOME_STATE, 1) to prefs.getInt(KEY_LOCK_STATE, 0)

    fun scheduleLabel(context: Context, index: Int): String {
        val i = coerceScheduleIndex(index)
        return when (i) {
            0 -> context.getString(R.string.schedule_daily_7am)
            1 -> context.getString(R.string.schedule_6h)
            2 -> context.getString(R.string.schedule_12h)
            else -> context.getString(R.string.schedule_24h)
        }
    }

    fun readTags(prefs: SharedPreferences): Pair<Array<String>, Array<String>> {
        val saved = prefs.getStringSet(KEY_SAVED_TAGS, emptySet()) ?: emptySet()
        val strict = mutableListOf<String>()
        val soft = mutableListOf<String>()
        saved.forEach { entry ->
            parseTagEntry(entry)?.let { (name, isStrict) ->
                if (isStrict) strict.add(name) else soft.add(name)
            } ?: soft.add(entry)
        }
        return strict.toTypedArray() to soft.toTypedArray()
    }

    fun formatTagEntry(tagName: String, isStrict: Boolean): String =
        "$tagName$TAG_ENTRY_SEP$isStrict"

    /** 解析标签条目；兼容旧版 `name|true` 格式。 */
    fun parseTagEntry(entry: String): Pair<String, Boolean>? {
        val sepIndex = entry.indexOf(TAG_ENTRY_SEP)
        if (sepIndex > 0) {
            val name = entry.substring(0, sepIndex)
            val flag = entry.substring(sepIndex + 1).toBooleanStrictOrNull() ?: return null
            return name to flag
        }
        val legacy = entry.split("|", limit = 2)
        if (legacy.size == 2) {
            return legacy[0] to legacy[1].toBoolean()
        }
        return null
    }

    fun isFirstLaunch(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_FIRST_LAUNCH, true)

    fun markFirstLaunchComplete(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun readRecentFetchedUrls(prefs: SharedPreferences): Set<String> {
        val fromList =
            prefs.getString(KEY_RECENT_FETCHED_URLS, "")
                ?.split('|')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
        val legacy = prefs.getString(KEY_LAST_FETCHED_URL, "")?.trim().orEmpty()
        if (legacy.isNotEmpty()) fromList.add(legacy)
        return fromList
    }

    fun appendRecentFetchedUrl(prefs: SharedPreferences, url: String) {
        if (url.isBlank()) return
        val list = readRecentFetchedUrls(prefs).toMutableList()
        list.remove(url)
        list.add(url)
        while (list.size > RECENT_URL_MAX) list.removeAt(0)
        prefs.edit()
            .putString(KEY_RECENT_FETCHED_URLS, list.joinToString("|"))
            .remove(KEY_LAST_FETCHED_URL)
            .apply()
    }

    fun clearRecentFetchedUrls(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_RECENT_FETCHED_URLS)
            .remove(KEY_LAST_FETCHED_URL)
            .apply()
    }

    fun prefetchSnapshotFingerprint(
        styleValue: Int,
        r18Mode: Int,
        homeState: Int,
        lockState: Int,
        strictTags: Array<String>,
        softTags: Array<String>,
    ): String {
        val strict = strictTags.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
        val soft = softTags.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
        return "$styleValue|$r18Mode|$homeState|$lockState|$strict|$soft"
    }

    fun prefetchSnapshotFingerprint(prefs: SharedPreferences): String {
        val (strict, soft) = readTags(prefs)
        return prefetchSnapshotFingerprint(
            styleValue = prefs.getInt(KEY_STYLE, DEFAULT_STYLE),
            r18Mode = prefs.getInt(KEY_R18_MODE, 0),
            homeState = prefs.getInt(KEY_HOME_STATE, 1),
            lockState = prefs.getInt(KEY_LOCK_STATE, 0),
            strictTags = strict,
            softTags = soft,
        )
    }

    fun saveBufferSourceUrl(
        prefs: SharedPreferences,
        slot: String,
        url: String,
        fingerprint: String? = null,
    ) {
        if (url.isBlank()) return
        val editor = prefs.edit().putString(bufferUrlKey(slot), url)
        if (fingerprint != null) {
            editor.putString(bufferFingerprintKey(slot), fingerprint)
        }
        editor.apply()
    }

    fun readBufferSourceUrl(prefs: SharedPreferences, slot: String): String? =
        prefs.getString(bufferUrlKey(slot), null)?.takeIf { it.isNotBlank() }

    fun readBufferFingerprint(prefs: SharedPreferences, slot: String): String? =
        prefs.getString(bufferFingerprintKey(slot), null)?.takeIf { it.isNotBlank() }

    fun clearBufferSourceUrls(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_BUFFER_URL_A)
            .remove(KEY_BUFFER_URL_B)
            .remove(KEY_BUFFER_FP_A)
            .remove(KEY_BUFFER_FP_B)
            .apply()
    }

    fun clearBufferSourceUrl(prefs: SharedPreferences, slot: String) {
        prefs.edit()
            .remove(bufferUrlKey(slot))
            .remove(bufferFingerprintKey(slot))
            .apply()
    }

    private fun bufferUrlKey(slot: String): String =
        if (slot == "b") KEY_BUFFER_URL_B else KEY_BUFFER_URL_A

    private fun bufferFingerprintKey(slot: String): String =
        if (slot == "b") KEY_BUFFER_FP_B else KEY_BUFFER_FP_A

    fun isAutoUpdateEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_DAILY_ENABLED, false)

    fun canApplyWallpaper(prefs: SharedPreferences): Boolean {
        val (home, lock) = readHomeLockState(prefs)
        return home > 0 || lock > 0
    }

    fun setPendingAutoFailure(prefs: SharedPreferences, pending: Boolean) {
        prefs.edit().putBoolean(KEY_PENDING_AUTO_FAILURE, pending).apply()
    }

    /** 读取并清除「自动换壁纸失败待提示」标志。 */
    fun consumePendingAutoFailure(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(KEY_PENDING_AUTO_FAILURE, false)) return false
        prefs.edit().remove(KEY_PENDING_AUTO_FAILURE).apply()
        return true
    }
}
