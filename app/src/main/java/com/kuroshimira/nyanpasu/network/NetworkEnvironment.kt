package com.kuroshimira.nyanpasu.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 当前网络路由：用于镜像顺序、超时与 Pixiv 解析策略。 */
enum class NetworkRoute {
    OFFLINE,
    /** 有 INTERNET 但未 VALIDATED：大陆直连 / 弱网常见，不宜过早放弃。 */
    RESTRICTED,
    VPN,
    NORMAL,
}

object NetworkEnvironment {

    private const val LOLICON = "https://api.lolicon.app/setu/v2"
    private const val YETAL = "https://api.yetal.ml/setu/v2"

    fun classify(context: Context): NetworkRoute {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return NetworkRoute.OFFLINE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkRoute.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkRoute.OFFLINE
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return NetworkRoute.VPN
        }
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return NetworkRoute.NORMAL
        }
        return NetworkRoute.RESTRICTED
    }

    /** Lolicon API 镜像尝试顺序。 */
    fun loliconMirrorOrder(route: NetworkRoute): List<String> =
        when (route) {
            NetworkRoute.RESTRICTED ->
                listOf(YETAL, LOLICON)
            NetworkRoute.VPN, NetworkRoute.NORMAL ->
                listOf(LOLICON, YETAL)
            NetworkRoute.OFFLINE ->
                listOf(LOLICON, YETAL)
        }

    /** 大陆/弱验证网络优先走 pixiv.cat 等镜像，减少 Ajax 超时。 */
    fun preferPixivMirrorFirst(route: NetworkRoute): Boolean =
        route == NetworkRoute.RESTRICTED

    fun logLabel(route: NetworkRoute): String = route.name.lowercase()
}
