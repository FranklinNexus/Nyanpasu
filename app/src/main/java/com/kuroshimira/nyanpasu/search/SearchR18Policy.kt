package com.kuroshimira.nyanpasu.search

/**
 * Lolicon / 图源链 R18 策略：与 MainActivity 三档口味一致（0=PURE，1=NSFW，2=MIX）。
 */
internal object SearchR18Policy {

    fun tierEscalations(appMode: Int): List<Int> =
        when (appMode) {
            0 -> emptyList()
            1 -> listOf(2)
            else -> listOf(1, 0)
        }

    fun fallbackLoliconChain(appMode: Int): List<Int> =
        when (appMode) {
            0 -> listOf(0)
            1 -> listOf(1, 2)
            else -> listOf(2, 1, 0)
        }

    fun skipLoliconItemsMarkedR18(appMode: Int): Boolean = appMode == 0 || appMode == 2
}
