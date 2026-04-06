package com.kuroshimira.nyanpasu

import android.content.Context
import coil.ImageLoader
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Coil 拉 Pixiv 镜像图时常需 Referer，否则会 403。
 */
object NetworkImageLoader {

    fun forApp(context: Context): ImageLoader {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val req = chain.request()
                        val host = req.url.host.lowercase()
                        val needReferer =
                            host.contains("pixiv") ||
                                host.contains("pximg") ||
                                host.endsWith(".teimg.com")
                        val next =
                            if (needReferer) {
                                req.newBuilder()
                                    .header("Referer", "https://www.pixiv.net/")
                                    .header(
                                        "User-Agent",
                                        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                                            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Nyanpasu/1.0",
                                    )
                                    .build()
                            } else {
                                req
                            }
                        chain.proceed(next)
                    },
                )
                .build()
        return ImageLoader.Builder(context.applicationContext)
            .okHttpClient(client)
            .build()
    }
}
