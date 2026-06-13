package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.network.NetworkEnvironment
import com.kuroshimira.nyanpasu.network.NetworkRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEnvironmentTest {

    @Test
    fun restricted_prefersDomesticMirrorFirst() {
        val order = NetworkEnvironment.loliconMirrorOrder(NetworkRoute.RESTRICTED)
        assertTrue(order.first().contains("yetal"))
    }

    @Test
    fun vpn_prefersLoliconFirst() {
        val order = NetworkEnvironment.loliconMirrorOrder(NetworkRoute.VPN)
        assertTrue(order.first().contains("lolicon"))
    }

    @Test
    fun restricted_prefersPixivMirror() {
        assertTrue(NetworkEnvironment.preferPixivMirrorFirst(NetworkRoute.RESTRICTED))
    }

    @Test
    fun normal_doesNotPreferPixivMirror() {
        assertEquals(false, NetworkEnvironment.preferPixivMirrorFirst(NetworkRoute.NORMAL))
    }
}
