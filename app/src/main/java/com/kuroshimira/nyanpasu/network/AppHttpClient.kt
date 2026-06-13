package com.kuroshimira.nyanpasu.network

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 全应用共享 OkHttp：JSON API 与图片下载共用 UA / Pixiv Referer 规则，超时按用途区分。
 */
object AppHttpClient {

    private const val TAG = "AppHttpClient"

    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Nyanpasu/1.0"

    private val uaInterceptor =
        Interceptor { chain ->
            var req = chain.request()
            if (req.header("User-Agent") == null) {
                req = req.newBuilder().header("User-Agent", BROWSER_UA).build()
            }
            chain.proceed(req)
        }

    private val pixivRefererInterceptor =
        Interceptor { chain ->
            var req = chain.request()
            val host = req.url.host.lowercase()
            val needReferer =
                host.contains("pixiv") ||
                    host.contains("pximg") ||
                    host.endsWith(".pixiv.re") ||
                    host == "pixiv.re" ||
                    host.endsWith(".teimg.com")
            if (needReferer && req.header("Referer") == null) {
                req = req.newBuilder().header("Referer", "https://www.pixiv.net/").build()
            }
            chain.proceed(req)
        }

    /** 搜索 / Booru / Ajax 等短超时 API。 */
    val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .addInterceptor(uaInterceptor)
            .addInterceptor(pixivRefererInterceptor)
            .build()
    }

    /** Coil 拉图：略宽裕但仍有总时限，避免一次失败卡太久。 */
    val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(28, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .addInterceptor(uaInterceptor)
            .addInterceptor(pixivRefererInterceptor)
            .build()
    }

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            getStringBlocking(url, headers)
        }

    fun getStringBlocking(url: String, headers: Map<String, String> = emptyMap()): String? =
        try {
            val request = buildGet(url, headers)
            apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET failed $url: ${e.message}")
            null
        }

    suspend fun probeUrl(url: String): Boolean =
        withContext(Dispatchers.IO) {
            if (httpProbe(url, "HEAD")) return@withContext true
            httpProbe(url, "GET", rangeBytes = true)
        }

    /** @see probeUrl */
    fun probeUrlBlocking(url: String): Boolean {
        if (httpProbe(url, "HEAD")) return true
        return httpProbe(url, "GET", rangeBytes = true)
    }

    private fun httpProbe(url: String, method: String, rangeBytes: Boolean = false): Boolean =
        try {
            val builder = Request.Builder().url(url)
            val request =
                if (method == "HEAD") {
                    builder.head().build()
                } else {
                    if (rangeBytes) builder.header("Range", "bytes=0-0")
                    builder.get().build()
                }
            apiClient.newCall(request).execute().use { response ->
                response.code in 200..399
            }
        } catch (e: Exception) {
            Log.d(TAG, "probe $method $url: ${e.message}")
            false
        }

    private fun buildGet(url: String, headers: Map<String, String>): Request {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }
}
