package pjs.dogbox.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object OkHttpUtil {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private var client: OkHttpClient = defaultClient()

    fun setClient(customClient: OkHttpClient) {
        client = customClient
    }

    private fun defaultClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }


    private fun buildHeaders(headers: Map<String, String>): Headers {
        val finalHeaders =
            if (headers.containsKey("User-Agent") || headers.containsKey("user-agent")) {
                headers
            } else {
                headers.toMutableMap().apply { put("User-Agent", AppData.UA_CHROME_PC) }
            }
        return finalHeaders.toHeaders()
    }


    fun get(url: String, headers: Map<String, String> = emptyMap()): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
            }
            response.body.string()
        }
    }

    fun getBytes(
        url: String,
        connectTimeout: Long = 5,
        readTimeout: Long = 5
    ): Result<ByteArray> = runCatching {
        val localClient = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(emptyMap()))
            .get()
            .build()
        localClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
            }
            response.body.bytes()
        }
    }


    fun post(
        url: String,
        body: String,
        mediaType: MediaType = JSON_MEDIA_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> =
        post(url, body.toRequestBody(mediaType), headers)


    fun post(
        url: String,
        requestBody: RequestBody,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
            }
            response.body.string()
        }
    }

    fun put(
        url: String,
        body: String,
        mediaType: MediaType = JSON_MEDIA_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> =
        put(url, body.toRequestBody(mediaType), headers)

    fun put(
        url: String,
        requestBody: RequestBody,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))
            .put(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
            }
            response.body.string()
        }
    }


    fun delete(url: String, headers: Map<String, String> = emptyMap()): Result<String> =
        runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(buildHeaders(headers))
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
                }
                response.body.string()
            }
        }


    fun delete(
        url: String,
        requestBody: RequestBody,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))
            .delete(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
            }
            response.body.string()
        }
    }


    fun patch(
        url: String,
        body: String,
        mediaType: MediaType = JSON_MEDIA_TYPE,
        headers: Map<String, String> = emptyMap()
    ): Result<String> =
        patch(url, body.toRequestBody(mediaType), headers)

    fun patch(
        url: String,
        requestBody: RequestBody,
        headers: Map<String, String> = emptyMap()
    ): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))
            .patch(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
            }
            response.body.string()
        }
    }


    fun head(url: String, headers: Map<String, String> = emptyMap()): Result<Map<String, String>> =
        runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(buildHeaders(headers))
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")
                }
                response.headers.toMultimap().mapValues { (_, values) -> values.joinToString(", ") }
            }
        }

    suspend fun getAsync(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) { get(url, headers).getOrThrow() }

    suspend fun postAsync(
        url: String,
        body: String,
        mediaType: MediaType = JSON_MEDIA_TYPE,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) { post(url, body, mediaType, headers).getOrThrow() }

    class HttpException(val statusCode: Int, message: String) : Exception(message)
}