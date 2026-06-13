package com.kuroshimira.nyanpasu.network

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 全应用共享 OkHttp：JSON API 与图片下载共用 UA / Pixiv Referer 规则。
 * 搜索 API 带重试；弱网环境下由 [NetworkEnvironment] 调整镜像顺序。
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

    /** 搜索 / Booru / Ajax：适度超时 + 失败重试。 */
    val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .addInterceptor(uaInterceptor)
            .addInterceptor(pixivRefererInterceptor)
            .build()
    }

    /** Coil 拉图：弱网/VPN 下需要更长的读超时。 */
    val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(55, TimeUnit.SECONDS)
            .addInterceptor(uaInterceptor)
            .addInterceptor(pixivRefererInterceptor)
            .build()
    }

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            getStringBlocking(url, headers)
        }

    /** 同一 URL 最多 3 次短退避，应对瞬时断连 / DNS 抖动。 */
    suspend fun getStringResilient(
        url: String,
        headers: Map<String, String> = emptyMap(),
        attempts: Int = 3,
    ): String? {
        val backoffMs = longArrayOf(0L, 350L, 900L)
        repeat(attempts.coerceAtMost(backoffMs.size)) { attempt ->
            if (attempt > 0) delay(backoffMs[attempt])
            getString(url, headers)?.let { return it }
        }
        return null
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
