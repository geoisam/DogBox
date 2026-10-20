package pjs.dogbox.ui.screens.discover

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pjs.dogbox.R
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.services.MediaDownloadService
import pjs.dogbox.ui.components.MiuixSubScreen
import pjs.dogbox.utils.network.HttpUtil
import pjs.dogbox.utils.security.CryptUtil
import pjs.dogbox.utils.system.openPermissionManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URLEncoder
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private data class AnalyzeSource(val key: String, val name: String, val desc: String)

private data class AnalyzeResult(
    val title: String,
    val videoUrls: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val musicUrl: String = "",
) {
    val isEmpty: Boolean get() = videoUrls.isEmpty() && imageUrls.isEmpty() && musicUrl.isBlank()
}

private val analyzeResultSaver = Saver<AnalyzeResult?, List<String>>(
    save = { res ->
        listOf(
            res?.title ?: "",
            res?.videoUrls?.joinToString("\n") ?: "",
            res?.imageUrls?.joinToString("\n") ?: "",
            res?.musicUrl ?: "",
        )
    },
    restore = { saved ->
        val title = saved.getOrNull(0) ?: ""
        val videos = saved.getOrNull(1).orEmpty()
            .takeIf { it.isNotEmpty() }?.split("\n") ?: emptyList()
        val images = saved.getOrNull(2).orEmpty()
            .takeIf { it.isNotEmpty() }?.split("\n") ?: emptyList()
        val music = saved.getOrNull(3) ?: ""
        if (title.isEmpty() && videos.isEmpty() && images.isEmpty() && music.isEmpty()) null
        else AnalyzeResult(title, videos, images, music)
    },
)

private val analyzeSources = listOf(
    AnalyzeSource(
        "JY",
        "JY线路",
        "支持抖音、快手、小红书、微信、B站、豆包、知乎、微博、西瓜、头条、火山、A站、最右、皮皮虾等",
    ),
    AnalyzeSource(
        "EL",
        "EL线路",
        "支持抖音、快手、小红书、微信、B站、豆包、知乎、微博、西瓜、头条、火山、A站、最右、皮皮虾等",
    ),
    AnalyzeSource(
        "AH",
        "AH线路",
        "支持抖音、快手、小红书、微信、B站、豆包、知乎、微博、西瓜、头条、火山、A站、最右、皮皮虾等",
    ),
    AnalyzeSource(
        "QT",
        "QT线路",
        "支持抖音、快手、小红书、B站、豆包、即梦、小云雀、千问、西瓜、头条、A站、最右、皮皮虾等",
    ),
    AnalyzeSource("LX", "LX线路", "支持抖音、快手、B站等"),
    AnalyzeSource("HS", "HS线路", "支持抖音、小红书等"),
)

internal fun extractShareUrl(input: String): String? {
    val text = input.trim()
    val url = Regex("""https?://[^\s"'<>，。]+""").find(text)?.value ?: return null
    return url.trimEnd('.', ',', ';', ')', ']', '}', '。', '，', '；', '】', '）', '」', '』', '"', '\'')
}

private fun jingYinUrl(link: String, isDouyin: Boolean): String {
    val encoded = URLEncoder.encode(link, "UTF-8")
    return if (isDouyin) {
        "https://api.7a.ink/jingyin/ApiProi.php?act=jx&priority=33&url=$encoded"
    } else {
        "https://api.7a.ink/jingyin/ApiProi.php?act=jx&url=$encoded"
    }
}

private fun aHuaUrl(link: String): String =
    "https://api.bugpk.com/api/short_videos?url=${URLEncoder.encode(link, "UTF-8")}"

private fun erLvUrl(link: String): String =
    "https://proxy.layzz.cn/lyz/getAnalyse?token=rzwewdzrckc-auther-523dddWEed&link=${
        URLEncoder.encode(
            link,
            "UTF-8"
        )
    }"

private fun lingXingUrl(link: String): String =
    "https://api.mcasx.com/api/spjx/?url=${URLEncoder.encode(link, "UTF-8")}"

private fun hongShuUrl(link: String): String =
    "https://async-dev.fun/api/xhs/post?user_id=ohyol7X24fMXmQgRx-Ky4Ixfb918&url=${
        URLEncoder.encode(
            link,
            "UTF-8"
        )
    }"

private suspend fun qingTingUrl(link: String): AnalyzeResult? {
    val appKey = "Nk7Qp4Lm2Vz8Hs1C"
    val nonceID = generateRandomID()
    val timestamp = (System.currentTimeMillis() / 1000).toString()
    val sign = CryptUtil.md5(
        "AppKey${appKey}AppVersion2.0.0Nonce${nonceID}Timestamp${timestamp}rT6mX9QaLp2Nd7FsKc4Hv8Jy",
        32,
        false,
    )
    val headers = mapOf(
        "nonce" to nonceID,
        "timestamp" to timestamp,
        "sign" to sign,
        "app-version" to "2.0.0",
        "origin" to "https://33tool.com",
        "referer" to "https://33tool.com/",
        "user-agent" to AppConfig.UA_WECHAT_PC,
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache",
    )
    // QT 接口为 POST，偶发网络抖动/超时，重试一次
    repeat(2) { attempt ->
        val body = buildJsonObject { put("url", link) }.toString()
        val result =
            HttpUtil.post("https://api.33tool.com/api/client/parse/all", body, headers = headers)
        val response = result.getOrNull()
        if (response != null) {
            val json = runCatching { Json.parseToJsonElement(response) }.getOrNull()
            if (json != null) {
                val parsed = parseQingTing(json)
                if (!parsed.isEmpty) return parsed
                // 解析结果为空可能是接口返回错误码，重试一次
                if (attempt == 0) delay(600)
                else return parsed
            }
        }
        if (attempt == 0) delay(600)
    }
    return null
}

fun generateRandomID(length: Int = 11): String {
    val allowedChars = ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

private fun JsonElement?.asUrlList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { it.jsonPrimitive.contentOrNull }
    else -> listOfNotNull(this?.jsonPrimitive?.contentOrNull)
}

private fun JsonElement?.asObjectFieldList(field: String): List<String> {
    val array = this as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        item.jsonObject[field]?.jsonPrimitive?.contentOrNull
    }
}

private fun JsonElement?.firstUrl(): String {
    val el = this ?: return ""
    return when (el) {
        is JsonArray -> el.mapNotNull { it.firstUrl().ifBlank { null } }.firstOrNull() ?: ""
        is JsonObject -> el["url"]?.jsonPrimitive?.contentOrNull ?: ""
        else -> el.jsonPrimitive.contentOrNull ?: ""
    }
}

private fun distinct(urls: List<String>): List<String> {
    val seen = HashSet<String>()
    return urls.filter { it.isNotBlank() && seen.add(it) }
}

private fun isImageUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("aweme-images") ||
            lower.contains("pcweb_cover") ||
            Regex("""\.(webp|jpe?g|png|gif|bmp)(\?|$)""").containsMatchIn(lower)
}

private fun parseJingYin(json: JsonElement): AnalyzeResult {
    val obj = json.jsonObject
    val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
    val images = buildList {
        addAll(obj["pics"].asUrlList())
        addAll(obj["picshh"].asObjectFieldList("image"))
    }
    val videos = buildList {
        addAll(obj["picshh"].asObjectFieldList("video"))
        addAll(obj["downurl"].asUrlList())
    }
    return AnalyzeResult(title, distinct(videos), distinct(images))
}

private fun parseAHua(json: JsonElement): AnalyzeResult {
    val data = json.jsonObject["data"]?.jsonObject ?: return AnalyzeResult("")
    val title = data["title"]?.jsonPrimitive?.contentOrNull ?: ""
    val images = buildList {
        addAll(data["images"].asUrlList())
        addAll(data["live_photo"].asObjectFieldList("image"))
    }
    val videos = buildList {
        addAll(data["url"].asUrlList())
        addAll(data["live_photo"].asObjectFieldList("video"))
    }
    val music = data["music"]?.firstUrl() ?: ""
    return AnalyzeResult(title, distinct(videos), distinct(images), music)
}

private fun parseErLv(json: JsonElement): AnalyzeResult {
    val data = json.jsonObject["data"]?.jsonObject ?: return AnalyzeResult("")
    val title = data["desc"]?.jsonPrimitive?.contentOrNull ?: ""
    val images = data["pics"].asUrlList()
    val videos = buildList {
        data["playAddr"]?.jsonPrimitive?.contentOrNull?.let { add(it) }
        addAll(data["videos"].asUrlList())
    }
    val music = data["music"]?.firstUrl() ?: ""
    return AnalyzeResult(title, distinct(videos), distinct(images), music)
}

private fun parseLingXing(json: JsonElement): AnalyzeResult {
    val obj = json.jsonObject
    val title = obj["视频标题"]?.jsonPrimitive?.contentOrNull
        ?: obj["标题"]?.jsonPrimitive?.contentOrNull
        ?: ""
    val images = obj["图集"].asUrlList()
    val videos = buildList {
        addAll(obj["实况视频"].asUrlList())
        addAll(obj["下载地址"].asUrlList().filter { !isImageUrl(it) })
    }
    val music = obj["背景音乐"]?.firstUrl()
        ?.takeIf { it.startsWith("http", ignoreCase = true) } ?: ""
    return AnalyzeResult(title, distinct(videos), distinct(images), music)
}

private fun parseHongShu(json: JsonElement): AnalyzeResult {
    val data = json.jsonObject["data"]?.jsonObject ?: return AnalyzeResult("")
    val title = data["note_text"]?.jsonPrimitive?.contentOrNull
        ?: data["title"]?.jsonPrimitive?.contentOrNull
        ?: ""
    val images = data["image_list"].asUrlList()
        .filter { !it.contains("sc=cover") && !it.contains("pcweb_cover") }
    val videos = buildList {
        addAll(data["live_list"].asObjectFieldList("url"))
        addAll(data["video_url"].asUrlList())
    }
    return AnalyzeResult(title, distinct(videos), distinct(images))
}

private fun parseQingTing(json: JsonElement): AnalyzeResult {
    val data = json.jsonObject["data"]?.jsonObject ?: return AnalyzeResult("")
    val title = data["title"]?.jsonPrimitive?.contentOrNull ?: ""
    val images = buildList {
        addAll(data["images"].asObjectFieldList("url"))
    }
    val videos = buildList {
        addAll(data["videoUrl"].asUrlList())
        addAll(data["images"].asObjectFieldList("livePhotoUrl"))
    }
    val music = (data["musicUrl"] as? JsonPrimitive)?.firstUrl()
        ?.takeIf { it.startsWith("http", ignoreCase = true) } ?: ""

    return AnalyzeResult(title, distinct(videos), distinct(images), music)
}

private suspend fun analyzeWith(source: String, link: String): AnalyzeResult? =
    withContext(Dispatchers.IO) {
        if (source == "QT") {
            return@withContext qingTingUrl(link)
        }
        val baseApiUrl = when (source) {
            "JY" -> jingYinUrl(link, link.contains("douyin", ignoreCase = true))
            "AH" -> aHuaUrl(link)
            "EL" -> erLvUrl(link)
            "LX" -> lingXingUrl(link)
            "HS" -> hongShuUrl(link)
            else -> return@withContext null
        }
        // 破缓存：每次追加时间戳，避免 OkHttp/Ktor 复用旧连接返回的缓存/304，以及服务端对同一 URL 的短时间去重限流
        val apiUrl =
            baseApiUrl + (if (baseApiUrl.contains("?")) "&" else "?") + "_t=${System.currentTimeMillis()}"
        val noCacheHeaders = mapOf(
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
        )
        var lastResult: AnalyzeResult? = null
        repeat(2) { attempt ->
            val body = HttpUtil.get(apiUrl, headers = noCacheHeaders).getOrNull()
            if (body != null) {
                val json = runCatching { Json.parseToJsonElement(body) }.getOrNull()
                if (json != null) {
                    val parsed = when (source) {
                        "JY" -> parseJingYin(json)
                        "AH" -> parseAHua(json)
                        "EL" -> parseErLv(json)
                        "LX" -> parseLingXing(json)
                        "HS" -> parseHongShu(json)
                        else -> null
                    }
                    if (parsed != null && !parsed.isEmpty) return@withContext parsed
                    lastResult = parsed
                }
            }
            if (attempt == 0) delay(600)
        }
        return@withContext lastResult
    }

internal fun copyText(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("素材链接", text))
    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun ClipboardActionRow(
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    TextActionButton(
        text = actionText,
        onClick = onAction,
        enabled = actionEnabled,
        modifier = modifier.fillMaxWidth(),
        containerColor = MiuixTheme.colorScheme.primary,
        textColor = MiuixTheme.colorScheme.onPrimary,
    )
}

@Composable
internal fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
) {
    val resolvedContainer = if (containerColor == Color.Unspecified) {
        MiuixTheme.colorScheme.surfaceContainerHigh
    } else containerColor
    val resolvedText = if (textColor == Color.Unspecified) {
        MiuixTheme.colorScheme.onSurface
    } else textColor
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(resolvedContainer.copy(alpha = if (enabled) 1f else 0.38f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = resolvedText.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

private fun hasFileAccessPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

private fun openFileAccessPermissionPage(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )
        }.onFailure { openPermissionManager(context) }
    } else {
        openPermissionManager(context)
    }
}

internal fun ensureFileAccess(context: Context): Boolean {
    if (hasFileAccessPermission(context)) return true
    Toast.makeText(context, "需要存储权限", Toast.LENGTH_SHORT).show()
    openFileAccessPermissionPage(context)
    return false
}

@Composable
fun MediaAnalyzeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenResult: (type: String, urls: List<String>, referer: String, title: String) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedSource by rememberSaveable { mutableStateOf(analyzeSources.first().key) }
    var linkInput by rememberSaveable { mutableStateOf("") }
    val sourceItems = remember {
        analyzeSources.map { DropdownItem(text = it.name, summary = it.desc) }
    }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by rememberSaveable(stateSaver = analyzeResultSaver) {
        mutableStateOf<AnalyzeResult?>(null)
    }
    var shareLink by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingDownload?.invoke()
        pendingDownload = null
    }

    // 防止快速二次点击产生并发，以及保证 isAnalyzing 在异常/取消时也能复位
    var analyzeJob by remember { mutableStateOf<Job?>(null) }

    fun ensureNotificationPermission(onGranted: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
            return true
        }
        pendingDownload = onGranted
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return false
    }

    fun startAnalyze() {
        if (isAnalyzing) return
        val input = linkInput.trim()
        if (input.isEmpty()) {
            Toast.makeText(context, "请输入分享链接", Toast.LENGTH_SHORT).show()
            return
        }
        val link = extractShareUrl(input)
        if (link == null) {
            Toast.makeText(context, "未识别到链接，请检查输入内容", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        // 只有当提取出的纯链接与原文不同时才回写，避免二次解析时因回写触发重组导致状态抖动
        if (link != linkInput) linkInput = link
        result = null
        shareLink = link
        isAnalyzing = true
        analyzeJob?.cancel()
        // 捕获当前线路，避免协程启动后用户切换线路导致结果错乱
        val currentSource = selectedSource
        analyzeJob = scope.launch {
            try {
                val res = analyzeWith(currentSource, link)
                if (res != null && !res.isEmpty) {
                    result = res
                } else {
                    Toast.makeText(context, "解析失败，请检查链接或稍后重试", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(context, "解析异常: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                isAnalyzing = false
            }
        }
    }

    fun stopAnalyze() {
        analyzeJob?.cancel()
        analyzeJob = null
        isAnalyzing = false
    }

    fun downloadMusic() {
        val res = result ?: return
        if (res.musicUrl.isBlank()) return
        if (!ensureFileAccess(context)) return
        ensureNotificationPermission {
            MediaDownloadService.start(
                context,
                listOf(res.musicUrl),
                shareLink,
                res.title,
                "Music",
                "mp3",
            )
            Toast.makeText(context, "开始下载，请查看通知栏进度", Toast.LENGTH_SHORT).show()
        }
    }

    MiuixSubScreen(
        title = "素材解析",
        onBack = onBack,
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
                insideMargin = PaddingValues(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.app_badging_outlined),
                        contentDescription = "通知",
                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MiuixText(
                        text = "仅供测试使用，禁止用于非法用途",
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                OverlaySpinnerPreference(
                    title = "解析线路",
                    dialogButtonString = "取消",
                    items = sourceItems,
                    selectedIndex = analyzeSources
                        .indexOfFirst { it.key == selectedSource }
                        .coerceAtLeast(0),
                    onExpandedChange = { expanded -> if (expanded) focusManager.clearFocus() },
                    onSelectedIndexChange = { index ->
                        analyzeSources.getOrNull(index)?.let { selectedSource = it.key }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(12.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = linkInput,
                            onValueChange = { linkInput = it },
                            label = "分享链接",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            maxLines = 6,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isAnalyzing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = { if (isAnalyzing) stopAnalyze() else startAnalyze() },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    MiuixText(
                        if (isAnalyzing) "停止解析" else "开始解析",
                        style = MiuixTheme.textStyles.title4,
                    )
                }
            }

            result?.let { res ->
                Spacer(modifier = Modifier.height(20.dp))
                MiuixText(
                    text = "解析结果",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (res.title.isNotBlank()) {
                    MiuixText(
                        text = res.title,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (res.imageUrls.isNotEmpty()) {
                        MediaTypeCard(
                            icon = R.drawable.photo_library_outlined,
                            label = "图片",
                            count = res.imageUrls.size,
                            onClick = {
                                onOpenResult(
                                    "image",
                                    res.imageUrls,
                                    shareLink,
                                    res.title
                                )
                            },
                            trailing = {
                                MiuixIcon(
                                    painter = painterResource(R.drawable.pending_outlined),
                                    contentDescription = "展开",
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                        )
                    }
                    if (res.videoUrls.isNotEmpty()) {
                        MediaTypeCard(
                            icon = R.drawable.video_library_outlined,
                            label = "视频",
                            count = res.videoUrls.size,
                            onClick = {
                                onOpenResult(
                                    "video",
                                    res.videoUrls,
                                    shareLink,
                                    res.title
                                )
                            },
                            trailing = {
                                MiuixIcon(
                                    painter = painterResource(R.drawable.pending_outlined),
                                    contentDescription = "展开",
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                        )
                    }
                    if (res.musicUrl.isNotBlank()) {
                        MediaTypeCard(
                            icon = R.drawable.library_music_outlined,
                            label = "背景音乐",
                            count = 1,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, res.musicUrl.toUri())
                                    )
                                }
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                            .clickable { copyText(context, res.musicUrl) }
                                            .padding(7.dp),
                                    ) {
                                        MiuixIcon(
                                            painter = painterResource(R.drawable.content_copy_outlined),
                                            contentDescription = "复制音乐链接",
                                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                            .clickable { downloadMusic() }
                                            .padding(7.dp),
                                    ) {
                                        MiuixIcon(
                                            painter = painterResource(R.drawable.cloud_download_outlined),
                                            contentDescription = "下载音乐",
                                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MediaTypeCard(
    icon: Int,
    label: String,
    count: Int,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MiuixIcon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            MiuixText(
                text = "$label（$count）",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
    }
}
