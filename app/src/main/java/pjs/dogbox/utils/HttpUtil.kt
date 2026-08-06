package pjs.dogbox.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object HttpUtil {

    private val JSON_CONTENT_TYPE = ContentType.parse("application/json; charset=utf-8")

    private val client: HttpClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 15_000
                socketTimeout = 15_000
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    private fun HttpRequestBuilder.applyHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) -> header(name, value) }
        if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
            header("User-Agent", AppData.UA_CHROME_PC)
        }
    }

    private fun HttpResponse.requireSuccess() {
        if (status.value !in 200..299) {
            throw HttpException(status.value, "HTTP ${status.value}: ${status.description}")
        }
    }

    private fun <T> blockingCall(block: suspend () -> T): Result<T> = runCatching {
        runBlocking { block() }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): Result<String> =
        blockingCall {
            val response = client.get(url) { applyHeaders(headers) }
            response.requireSuccess()
            response.bodyAsText()
        }

    fun getBytes(
        url: String,
        connectTimeout: Long = 5,
        readTimeout: Long = 5
    ): Result<ByteArray> = blockingCall {
        val response = client.get(url) {
            applyHeaders(emptyMap())
            timeout {
                requestTimeoutMillis = connectTimeout * 1000
                connectTimeoutMillis = connectTimeout * 1000
                socketTimeoutMillis = readTimeout * 1000
            }
        }
        response.requireSuccess()
        response.bodyAsBytes()
    }

    fun post(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = blockingCall {
        val response = client.post(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    fun put(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = blockingCall {
        val response = client.put(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    fun delete(url: String, headers: Map<String, String> = emptyMap()): Result<String> =
        blockingCall {
            val response = client.delete(url) { applyHeaders(headers) }
            response.requireSuccess()
            response.bodyAsText()
        }

    fun delete(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = blockingCall {
        val response = client.delete(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    fun patch(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = blockingCall {
        val response = client.patch(url) {
            applyHeaders(headers)
            contentType(contentType)
            setBody(body)
        }
        response.requireSuccess()
        response.bodyAsText()
    }

    fun head(url: String, headers: Map<String, String> = emptyMap()): Result<Map<String, String>> =
        blockingCall {
            val response = client.head(url) { applyHeaders(headers) }
            response.requireSuccess()
            response.headers.entries()
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, values) -> values.joinToString(", ") }
        }

    suspend fun getAsync(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) { get(url, headers).getOrThrow() }

    suspend fun postAsync(
        url: String,
        body: String,
        contentType: ContentType = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) { post(url, body, contentType, headers).getOrThrow() }

    class HttpException(val statusCode: Int, message: String) : Exception(message)
}