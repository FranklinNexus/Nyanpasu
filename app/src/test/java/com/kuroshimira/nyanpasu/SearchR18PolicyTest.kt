package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.search.SearchR18Policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchR18PolicyTest {

    @Test
    fun pureMode_noTierEscalation() {
        assertTrue(SearchR18Policy.tierEscalations(0).isEmpty())
    }

    @Test
    fun mixMode_skipsExplicitLoliconItems() {
        assertTrue(SearchR18Policy.skipLoliconItemsMarkedR18(2))
    }

    @Test
    fun nsfwFallbackChain() {
        assertEquals(listOf(1, 2), SearchR18Policy.fallbackLoliconChain(1))
    }

    @Test
    fun pureFallbackOnlySafe() {
        assertEquals(listOf(0), SearchR18Policy.fallbackLoliconChain(0))
        assertTrue(SearchR18Policy.skipLoliconItemsMarkedR18(0))
    }
}
