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

    /** Lolicon API 镜像尝试顺序。VPN 下仍优先 yetal（大陆常见：梯子不接管 App 流量）。 */
    fun loliconMirrorOrder(route: NetworkRoute): List<String> =
        when (route) {
            NetworkRoute.RESTRICTED, NetworkRoute.VPN ->
                listOf(YETAL, LOLICON)
            NetworkRoute.NORMAL ->
                listOf(LOLICON, YETAL)
            NetworkRoute.OFFLINE ->
                listOf(YETAL, LOLICON)
        }

    /** 大陆/弱验证/VPN 网络优先走 pixiv 反代，避免 Ajax 返回 pximg 直连链。 */
    fun preferPixivMirrorFirst(route: NetworkRoute): Boolean =
        route == NetworkRoute.RESTRICTED || route == NetworkRoute.VPN

    fun logLabel(route: NetworkRoute): String = route.name.lowercase()
}
