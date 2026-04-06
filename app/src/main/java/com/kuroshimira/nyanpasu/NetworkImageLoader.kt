package com.kuroshimira.nyanpasu

import android.content.Context
import coil.ImageLoader
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Coil 拉 Pixiv 镜像图时常需 Referer，否则会 403。
 * 默认 OkHttp 读超时较短，壁纸原图在弱网/VPN 下易读超时；这里显式拉长并统一浏览器 UA。
 */
object NetworkImageLoader {

    private val browserUa =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Nyanpasu/1.0"

    fun forApp(context: Context): ImageLoader {
        val client =
            OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(
                    Interceptor { chain ->
                        var req = chain.request()
                        if (req.header("User-Agent") == null) {
                            req = req.newBuilder().header("User-Agent", browserUa).build()
                        }
                        val host = req.url.host.lowercase()
                        val needReferer =
                            host.contains("pixiv") ||
                                host.contains("pximg") ||
                                host.endsWith(".teimg.com")
                        val next =
                            if (needReferer) {
                                req.newBuilder()
                                    .header("Referer", "https://www.pixiv.net/")
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
