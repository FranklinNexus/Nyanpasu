package com.kuroshimira.nyanpasu.network

import android.os.Build

/** 网络是否可用于壁纸下载（从 [NetworkStatus.isConnected] 抽出，便于单测）。 */
internal fun isNetworkUsable(hasInternet: Boolean, hasValidated: Boolean, sdkInt: Int): Boolean {
    if (!hasInternet) return false
    if (sdkInt >= Build.VERSION_CODES.M) {
        if (hasValidated) return true
        return hasInternet
    }
    return true
}
