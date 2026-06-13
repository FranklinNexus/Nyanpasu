package com.kuroshimira.nyanpasu.network

import android.content.Context
import coil.ImageLoader

/** Coil 拉图入口；HTTP 栈与 [AppHttpClient.imageClient] 共用 UA / Referer 规则。 */
object NetworkImageLoader {

    fun forApp(context: Context): ImageLoader =
        ImageLoader.Builder(context.applicationContext)
            .okHttpClient(AppHttpClient.imageClient)
            .build()
}
