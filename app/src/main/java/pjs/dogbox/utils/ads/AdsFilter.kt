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
 *
 * 兼容两种结构：
 * - 一级 media playlist（直接包含 #EXTINF 分片）：下载后直接过滤；
 * - 二级 master playlist（仅含 #EXT-X-STREAM-INF 变体声明，如量子源）：广告位于
 *   变体指向的子 m3u8 中，必须下载子 m3u8 并过滤，再把本地 master 的变体地址指向
 *   过滤结果。仅过滤 master 本身对广告无效，会导致"开了过滤广告依旧播放"。
 */
object AdsFilter {

    private const val RULES_ASSET = "json/rules.json"
    private const val CACHE_PREFIX = "ads_filter"
    private const val CACHE_MEDIA_INFIX = "_media"
    private const val CACHE_EXTENSION = ".m3u8"
    private const val EXTM3U_HEADER = "#EXTM3U"
    private const val STREAM_INF_TAG = "#EXT-X-STREAM-INF"
    private const val ENDLIST_TAG = "#EXT-X-ENDLIST"

    /** 旧缓存文件的保留宽限期：播放器可能仍在使用上一轮写出的 playlist。 */
    private const val CACHE_GRACE_MS = 5 * 60 * 1000L

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
                // 每次过滤用唯一文件名，避免并发会话互相覆盖。
                // keep 收集本轮写出的全部文件名，最后统一清理其余旧文件：
                // 一轮过滤可能写出 1 个 master + N 个变体 media 文件，不能边写边删。
                val cacheTag = System.currentTimeMillis().toString()
                val keep = mutableSetOf<String>()
                try {
                    filterPlaylist(context, url, rule, cacheTag, keep)
                } finally {
                    if (keep.isNotEmpty()) cleanupStaleCache(context, keep)
                }
            }
        } catch (_: Exception) {
            url
        }
    }

    private suspend fun filterPlaylist(
        context: Context,
        url: String,
        rule: AdsRule,
        cacheTag: String,
        keep: MutableSet<String>,
    ): String {
        val playlist = HttpUtil.get(url).getOrThrow()
        if (!playlist.replace("\uFEFF", "").trimStart().startsWith(EXTM3U_HEADER)) return url
        return if (playlist.contains(STREAM_INF_TAG)) {
            filterMasterPlaylist(context, url, playlist, rule, cacheTag, keep)
        } else {
            filterMediaPlaylist(context, url, playlist, rule, cacheTag, keep, variantIndex = 0)
                ?: url
        }
    }

    /**
     * 过滤二级 master playlist：
     * 下载并过滤每个变体指向的子 m3u8（广告所在层），把 master 中的变体地址替换为
     * 本地过滤文件。任一变体过滤失败则整体回退原始链接，保证始终可播。
     */
    private suspend fun filterMasterPlaylist(
        context: Context,
        url: String,
        playlist: String,
        rule: AdsRule,
        cacheTag: String,
        keep: MutableSet<String>,
    ): String {
        val builder = StringBuilder(playlist.length + 128)
        var pendingVariant = false
        var filteredVariants = 0
        var variantIndex = 0
        val masterName = cacheFileName(cacheTag, isMedia = false)
        for (rawLine in playlist.lines()) {
            val trimmed = rawLine.trim()
            when {
                // 变体声明行：其属性内 URI（若有）先解析为绝对地址。
                // 注：变体地址写在 URI 属性、其后无独立地址行的写法极为罕见，
                // 此处按地址行形态处理，属性形态仅重写为绝对地址不过滤。
                trimmed.startsWith(STREAM_INF_TAG) -> {
                    builder.append(resolveTagUris(trimmed, url)).append("\r\n")
                    pendingVariant = true
                }

                // 变体地址行：指向真正的分片列表（广告所在），需下载并过滤。
                // 每个变体必须写独立缓存文件：若共用同一个文件名，后面的变体会覆盖前面的，
                // master 里所有变体最终指向同一份分片列表，码率/分辨率声明与实际内容不符，
                // 播放器自适应码率会不停切换同一个流，表现为卡顿、卡死后重缓冲。
                pendingVariant && !trimmed.startsWith("#") -> {
                    val variantUrl = resolveSegment(url, trimmed)
                    val index = ++variantIndex
                    val filteredVariant = runCatching {
                        val media = HttpUtil.get(variantUrl).getOrThrow()
                        if (!media.replace("\uFEFF", "").trimStart().startsWith(EXTM3U_HEADER)) {
                            null
                        } else {
                            filterMediaPlaylist(
                                context,
                                variantUrl,
                                media,
                                rule,
                                cacheTag,
                                keep,
                                variantIndex = index,
                            )
                        }
                    }.getOrNull()
                    if (filteredVariant == null) return url
                    builder.append(filteredVariant).append("\r\n")
                    pendingVariant = false
                    filteredVariants++
                }

                trimmed.startsWith("#") -> builder.append(resolveTagUris(trimmed, url)).append("\r\n")

                else -> builder.append(resolveSegment(url, trimmed)).append("\r\n")
            }
        }
        if (filteredVariants == 0) return url
        keep += masterName
        return saveToCache(context, builder.toString(), masterName)
    }

    /** 过滤一级 media playlist；过滤后不含任何分片则视为失败。 */
    private suspend fun filterMediaPlaylist(
        context: Context,
        url: String,
        playlist: String,
        rule: AdsRule,
        cacheTag: String,
        keep: MutableSet<String>,
        variantIndex: Int,
    ): String? {
        val filtered = preserveEndList(playlist, applyRegex(rule, playlist))
        val resolved = resolveUrls(filtered, url) ?: return null
        val fileName = cacheFileName(cacheTag, isMedia = true, variantIndex = variantIndex)
        keep += fileName
        return saveToCache(context, resolved, fileName)
    }

    private fun cacheFileName(tag: String, isMedia: Boolean, variantIndex: Int = 0): String {
        val mediaInfix = if (isMedia) CACHE_MEDIA_INFIX else ""
        val variantSuffix = if (isMedia && variantIndex > 0) "_v$variantIndex" else ""
        return "$CACHE_PREFIX$tag$mediaInfix$variantSuffix$CACHE_EXTENSION"
    }

    /**
     * 规则里的 `.*` 有可能把 `#EXT-X-ENDLIST` 一起吃掉，那样点播流会被当成直播流，
     * 播放器会周期性地重新读取本地 playlist，一旦文件被清理就整段卡死，所以这里补回来。
     */
    private fun preserveEndList(original: String, filtered: String): String =
        if (original.contains(ENDLIST_TAG) && !filtered.contains(ENDLIST_TAG)) {
            filtered.trimEnd('\r', '\n') + "\r\n" + ENDLIST_TAG + "\r\n"
        } else {
            filtered
        }

    /**
     * 清理历次过滤留下的旧缓存文件。
     *
     * 播放器并不是只在 prepare 时读一次 playlist：HLS 自适应切换码率、seek 到未缓冲区间、
     * 直播式（无 ENDLIST）playlist 刷新时都会重新读取本地文件，后台播放时旧影片也可能仍在用。
     * 因此只清理超过宽限期、确定无人引用的文件，绝不删除本轮刚写出的文件。
     */
    private fun cleanupStaleCache(context: Context, keep: Set<String>) {
        runCatching {
            val now = System.currentTimeMillis()
            context.cacheDir.listFiles()?.forEach { file ->
                val name = file.name
                val isCacheFile = name.startsWith(CACHE_PREFIX) &&
                        (name.endsWith(CACHE_EXTENSION) || name.endsWith("$CACHE_EXTENSION.tmp"))
                if (!isCacheFile || name in keep) return@forEach
                if (now - file.lastModified() < CACHE_GRACE_MS) return@forEach
                file.delete()
            }
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

    /**
     * 原子落盘：先写 .tmp 再 rename。
     *
     * 播放器按需重读 playlist（码率切换 / 刷新），如果直接原地覆盖，
     * 读到写了一半的文件就是解析失败；rename 是同分区原子操作，读者要么看到旧内容、
     * 要么看到完整新内容。
     */
    private fun saveToCache(context: Context, playlist: String, fileName: String): String {
        val file = File(context.cacheDir, fileName)
        val tmpFile = File(context.cacheDir, "$fileName.tmp")
        tmpFile.writeText(playlist)
        if (!tmpFile.renameTo(file)) {
            // rename 失败（极少数文件系统）时退回直接写
            runCatching { file.writeText(playlist) }
            runCatching { tmpFile.delete() }
        }
        return Uri.fromFile(file).toString()
    }
}
