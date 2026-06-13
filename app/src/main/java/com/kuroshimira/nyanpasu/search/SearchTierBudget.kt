package com.kuroshimira.nyanpasu.search

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** 分层搜索子超时：避免单层 HTTP 风暴占满 JobRunner 90s 总预算。 */
internal class SearchTierBudget(totalMs: Long) {

    private val deadlineMs = System.currentTimeMillis() + totalMs

    fun hasTime(): Boolean = System.currentTimeMillis() < deadlineMs

    suspend fun <T> run(tierCapMs: Long, block: suspend () -> T?): T? {
        if (!hasTime()) return null
        val remaining = deadlineMs - System.currentTimeMillis()
        if (remaining <= 0L) return null
        val cap = minOf(tierCapMs, remaining).coerceAtLeast(1L)
        return try {
            withTimeout(cap) { block() }
        } catch (_: TimeoutCancellationException) {
            null
        }
    }

    companion object {
        const val TOTAL_MS = 75_000L
        const val TIER1_MS = 35_000L
        const val TIER1A_MS = 12_000L
        const val TIER1B_MS = 8_000L
        const val TIER2_MS = 12_000L
        const val TIER3_MS = 8_000L
        const val TIER4_MS = 10_000L
    }
}
