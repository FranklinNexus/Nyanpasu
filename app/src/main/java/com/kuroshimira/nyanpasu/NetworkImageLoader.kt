package com.kuroshimira.nyanpasu

import android.content.Context
import coil.ImageLoader
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Coil 拉 Pixiv 镜像图时常需 Referer，否则会 403。
 * 统一浏览器 UA；超时取「略宽裕但快速失败」——过长会导致失败一次等几分钟。
 */
object NetworkImageLoader {

    private val browserUa =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Nyanpasu/1.0"

    fun forApp(context: Context): ImageLoader {
        val client =
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(28, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(40, TimeUnit.SECONDS)
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
