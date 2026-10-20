package pjs.dogbox.utils.ads

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.utils.network.HttpUtil
import java.io.File
import java.util.regex.Pattern

/**
 * M3U8 广告过滤（参考 FongMi/TV 的实现思路）：
 * 解析 assets/json/rules.json 中的规则，若播放链接的域名包含规则 hosts 中的任一关键字，
 * 则下载 m3u8 内容，按顺序应用该规则的正则移除广告片段，
 * 并将相对地址解析为绝对地址后写入缓存文件，返回本地文件地址供播放器播放。
 */
object AdsFilter {

    private const val RULES_ASSET = "json/rules.json"
    private const val CACHE_FILE_NAME = "ads_filter.m3u8"

    private data class AdsRule(
        val name: String,
        val hosts: List<String>,
        val regex: List<String>,
    )

    private val tagUriPattern = Pattern.compile("URI=\"([^\"]+)\"")

    @Volatile
    private var cachedRules: List<AdsRule>? = null

    suspend fun filter(context: Context, url: String, enabled: Boolean): String {
        if (!enabled || url.isEmpty()) return url
        val rule = matchRule(url, getRules(context)) ?: return url
        return try {
            withContext(Dispatchers.IO) {
                val playlist = HttpUtil.get(url).getOrThrow()
                if (!playlist.replace("\uFEFF", "").trimStart()
                        .startsWith("#EXTM3U")
                ) return@withContext url
                val filtered = applyRegex(rule, playlist)
                val resolved = resolveUrls(filtered, url) ?: return@withContext url
                saveToCache(context, resolved)
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun getRules(context: Context): List<AdsRule> {
        cachedRules?.let { return it }
        synchronized(this) {
            cachedRules?.let { return it }
            val parsed = runCatching {
                context.assets.open(RULES_ASSET).bufferedReader().use { reader ->
                    Json.parseToJsonElement(reader.readText()).jsonArray.mapNotNull { element ->
                        val obj = element.jsonObject
                        AdsRule(
                            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                            hosts = obj["hosts"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?: emptyList(),
                            regex = obj["regex"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?: emptyList(),
                        )
                    }
                }
            }.getOrDefault(emptyList())
            cachedRules = parsed
            return parsed
        }
    }

    private fun matchRule(url: String, rules: List<AdsRule>): AdsRule? {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return null
        if (host.isEmpty()) return null
        return rules.firstOrNull { rule ->
            rule.hosts.any { it.isNotEmpty() && host.contains(it, ignoreCase = true) }
        }
    }

    private fun applyRegex(rule: AdsRule, playlist: String): String {
        var result = playlist
        for (regex in rule.regex) {
            result = runCatching {
                Pattern.compile(regex).matcher(result).replaceAll("")
            }.getOrDefault(result)
        }
        return result
    }

    private fun resolveUrls(playlist: String, baseUrl: String): String? {
        val builder = StringBuilder(playlist.length + 128)
        var hasSegments = false
        for (line in playlist.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {}
                trimmed.startsWith("#") -> builder.append(resolveTagUris(trimmed, baseUrl))
                    .append("\r\n")

                else -> {
                    hasSegments = true
                    builder.append(resolveSegment(baseUrl, trimmed)).append("\r\n")
                }
            }
        }
        return if (hasSegments) builder.toString() else null
    }

    private fun resolveSegment(baseUrl: String, segment: String): String {
        if (segment.startsWith("http://", true) || segment.startsWith(
                "https://",
                true
            )
        ) return segment
        return runCatching {
            java.net.URI(baseUrl).resolve(java.net.URI(segment)).toString()
        }.getOrElse {
            if (segment.startsWith("/")) {
                val uri = Uri.parse(baseUrl)
                "${uri.scheme}://${uri.authority}$segment"
            } else {
                baseUrl.substringBeforeLast('/') + "/" + segment
            }
        }
    }

    private fun resolveTagUris(line: String, baseUrl: String): String {
        val matcher = tagUriPattern.matcher(line)
        val builder = StringBuilder(line.length + 64)
        var last = 0
        while (matcher.find()) {
            val resolved = resolveSegment(baseUrl, matcher.group(1) ?: continue)
            builder.append(line, last, matcher.start(1)).append(resolved)
            last = matcher.end(1)
        }
        if (last == 0) return line
        builder.append(line, last, line.length)
        return builder.toString()
    }

    private fun saveToCache(context: Context, playlist: String): String {
        val file = File(context.cacheDir, CACHE_FILE_NAME)
        file.writeText(playlist)
        return Uri.fromFile(file).toString()
    }
}
