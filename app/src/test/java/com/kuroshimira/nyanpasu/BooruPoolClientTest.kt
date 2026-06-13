package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.search.BooruPoolClient
import org.junit.Assert.assertEquals
import org.junit.Test

class BooruPoolClientTest {

    @Test
    fun normalizeMoeRating_mapsSingleLetterCodes() {
        assertEquals("safe", BooruPoolClient.normalizeMoeRating("s"))
        assertEquals("safe", BooruPoolClient.normalizeMoeRating("SAFE"))
        assertEquals("questionable", BooruPoolClient.normalizeMoeRating("q"))
        assertEquals("explicit", BooruPoolClient.normalizeMoeRating("e"))
        assertEquals("explicit", BooruPoolClient.normalizeMoeRating("explicit"))
    }
}
