package com.kuroshimira.nyanpasu.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkStatus {

    fun isConnected(context: Context): Boolean {
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
