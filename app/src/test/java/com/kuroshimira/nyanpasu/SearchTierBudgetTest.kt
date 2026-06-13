package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.search.SearchTierBudget
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTierBudgetTest {

    @Test
    fun hasTime_falseAfterDeadline() {
        val budget = SearchTierBudget(totalMs = 50L)
        Thread.sleep(60L)
        assertFalse(budget.hasTime())
    }

    @Test
    fun run_returnsNullWhenBudgetExpired() = runBlocking {
        val budget = SearchTierBudget(totalMs = 30L)
        delay(40L)
        assertNull(budget.run(SearchTierBudget.TIER1_MS) { "ok" })
    }

    @Test
    fun run_returnsBlockResultWithinCap() = runBlocking {
        val budget = SearchTierBudget(totalMs = 5_000L)
        assertEquals("tier1", budget.run(SearchTierBudget.TIER1_MS) { "tier1" })
    }

    @Test
    fun run_timesOutLongBlock() = runBlocking {
        val budget = SearchTierBudget(totalMs = 5_000L)
        val result =
            budget.run(tierCapMs = 50L) {
                delay(200L)
                "late"
            }
        assertNull(result)
    }

    @Test
    fun tierConstants_sumWithinTotal() {
        val tierSum =
            SearchTierBudget.TIER1_MS +
                SearchTierBudget.TIER2_MS +
                SearchTierBudget.TIER3_MS +
                SearchTierBudget.TIER4_MS
        assertTrue(tierSum <= SearchTierBudget.TOTAL_MS)
    }
}
