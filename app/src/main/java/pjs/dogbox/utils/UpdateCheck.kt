package pjs.dogbox.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UpdateInfo(
    val code: Int,
    val ver: String,
    val note: String,
    val down: String,
)

suspend fun fetchRemoteConfig(): kotlinx.serialization.json.JsonObject? {
    val urls = listOf(
        "https://gh-proxy.org/raw.githubusercontent.com/geoisam/geoisam/main/data/base.json",
        "https://ghfast.top/raw.githubusercontent.com/geoisam/geoisam/main/data/base.json",
        "https://ghproxy.net/raw.githubusercontent.com/geoisam/geoisam/main/data/base.json",
        "https://githubdog.com/raw.githubusercontent.com/geoisam/geoisam/main/data/base.json",
    )

    var result: kotlinx.serialization.json.JsonObject? = null
    coroutineScope {
        val deferred = urls.map { url ->
            async(Dispatchers.IO) {
                val bytes =
                    HttpUtil.getBytes(url).getOrNull()
                bytes?.let { parseRemoteConfig(it) }
            }
        }.toMutableList()
        while (deferred.isNotEmpty()) {
            val winner = select {
                deferred.forEach { deferred ->
                    deferred.onAwait { deferred }
                }
            }
            deferred.remove(winner)
            val r = winner.await()
            if (r != null) {
                result = r
                coroutineContext.cancelChildren()
                break
            }
        }
    }
    return result
}

private fun parseRemoteConfig(bytes: ByteArray): kotlinx.serialization.json.JsonObject? {
    return runCatching {
        Json.parseToJsonElement(bytes.decodeToString().trim()).jsonObject
    }.getOrNull()
}

fun parseUpdateInfo(configJson: kotlinx.serialization.json.JsonObject?): UpdateInfo? {
    val updateObj = configJson?.get("update")?.jsonObject ?: return null
    val vCode = updateObj["vCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
    return UpdateInfo(
        code = vCode,
        ver = updateObj["vName"]?.jsonPrimitive?.contentOrNull ?: "",
        note = updateObj["vNote"]?.jsonPrimitive?.contentOrNull ?: "",
        down = updateObj["vDown"]?.jsonPrimitive?.contentOrNull ?: "",
    )
}
