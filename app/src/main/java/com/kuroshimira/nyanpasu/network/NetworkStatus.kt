package com.kuroshimira.nyanpasu.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkStatus {

    fun isConnected(context: Context): Boolean = shouldAttemptNetworkWork(context)

    /** 是否应尝试联网任务：有 INTERNET 即可，不因 VALIDATED 未就绪直接放弃。 */
    fun shouldAttemptNetworkWork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return isNetworkUsable(
            hasInternet = true,
            hasValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            sdkInt = Build.VERSION.SDK_INT,
        )
    }
}
