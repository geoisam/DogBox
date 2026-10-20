package pjs.dogbox.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import okhttp3.OkHttpClient
import okhttp3.Request
import pjs.dogbox.data.AppData
import java.util.concurrent.TimeUnit

object HttpUtil {

    private val JSON_CONTENT_TYPE = ContentType.parse("application/json; charset=utf-8")

    /** 全局 OkHttp 连接池，Ktor 客户端与 Coil 图片加载共享 */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .apply {
                // 素材解析网格会同时发起 20-30 张图片请求，默认 perHost=5 会导致大量排队超时
                // 提升并发以避免“经常性加载失败”假象（实际是排队+超时被当成功能失败）
                dispatcher(okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 16
                })
            }
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent") == null) {
                    chain.proceed(
                        request.newBuilder().header("User-Agent", AppData.UA_CHROME_M).build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    /** 关闭自动重定向，用于手动跟随并获取最终 URL */
    private val noRedirectClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private fun HttpRequestBuilder.applyHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) -> header(name, value) }
        if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
            header("User-Agent", AppData.UA_CHROME_PC)
        }
    }

    private suspend fun HttpResponse.requireSuccess() {
        if (status.value !in 200..299) {
            bodyAsBytes() // consume/drain body so the connection is released back to the pool cleanly
            throw HttpException(status.value, "HTTP ${status.value}: ${status.description}")
        }
    }

    private inline suspend fun <T> suspendCatching(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Throwable) {
            Result.failure(e)
        }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Result<String> =
        suspendCatching {
            val response = client.get(url) { applyHeaders(headers) }
            response.requireSuccess()
            response.bodyAsText()
        }

    suspend fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeout: Long = 5,
        readTimeout: Long = 5
    ): Result<ByteArray> = suspendCatching {
        val response = client.get(url) {
            applyHeaders(headers)
            timeout {
                requestTimeoutMillis = connectTimeout * 1000
                connectTimeoutMillis = connectTimeout * 1000
                socketTimeoutMillis = readTimeout * 1000
            }
        }
        response.requireSuccess()
        response.bodyAsBytes()
    }

    /** 跟随重定向后返回最终 URL */
    suspend fun getFinalUrl(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxRedirects: Int = 10,
    ): Result<String> = suspendCatching {
        var current = url
        repeat(maxRedirects) {
            val request = Request.Builder()
                .url(current)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            noRedirectClient.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    val location = response.header("Location")
                    if (location.isNullOrBlank()) return@suspendCatching current
                    val resolved = response.request.url.resolve(location)
                    if (resolved == null) return@suspendCatching current
                    current = resolved.toString()
                } else {
                    return@suspendCatching current
                }
            }
        }
        current
    }

    suspend fun post(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = suspendCatching {
        val response = client.post(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    suspend fun put(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = suspendCatching {
        val response = client.put(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): Result<String> =
        suspendCatching {
            val response = client.delete(url) { applyHeaders(headers) }
            response.requireSuccess()
            response.bodyAsText()
        }

    suspend fun delete(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = suspendCatching {
        val response = client.delete(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    suspend fun patch(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = suspendCatching {
        val response = client.patch(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Result<Map<String, String>> =
        suspendCatching {
            val response = client.head(url) { applyHeaders(headers) }
            response.requireSuccess()
            response.headers.entries()
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, values) -> values.joinToString(", ") }
        }

    class HttpException(val statusCode: Int, message: String) : Exception(message)
}