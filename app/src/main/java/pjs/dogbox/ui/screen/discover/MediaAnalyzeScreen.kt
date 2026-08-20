package pjs.dogbox.ui.screen.discover

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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.service.MediaDownloadService
import pjs.dogbox.ui.screen.mine.openPermissionManager
import pjs.dogbox.utils.AppData
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.utils.SimpleDragHandle
import java.net.URLEncoder

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
        "支持抖音、快手、小红书、微信、豆包、知乎、微博、YouTube、TikTok、Instagram、X(Twitter)等",
    ),
    AnalyzeSource(
        "AH",
        "AH线路",
        "支持抖音、快手、小红书、微信、豆包、知乎、微博、YouTube、TikTok、Instagram、X(Twitter)等",
    ),
    AnalyzeSource(
        "EL",
        "EL线路",
        "支持抖音、快手、小红书、微信、豆包、知乎、微博、YouTube、TikTok、Instagram、X(Twitter)等",
    ),
    AnalyzeSource("LX", "LX线路", "支持抖音、快手等"),
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
    "https://proxy.layzz.cn/lyz/getAnalyse?token=rzwewdzrckc-auther-523dddWEed&link=${URLEncoder.encode(link, "UTF-8")}"

private fun lingXingUrl(link: String): String =
    "https://api.mcasx.com/api/spjx/?url=${URLEncoder.encode(link, "UTF-8")}"

private fun hongShuUrl(link: String): String =
    "https://async-dev.fun/api/xhs/post?user_id=ohyol7X24fMXmQgRx-Ky4Ixfb918&url=${URLEncoder.encode(link, "UTF-8")}"

private fun JsonElement?.asStringList(): List<String> {
    val array = this as? JsonArray ?: return emptyList()
    return array.mapNotNull { it.jsonPrimitive.contentOrNull }
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

private suspend fun analyzeWith(source: String, link: String): AnalyzeResult? = withContext(Dispatchers.IO) {
    val apiUrl = when (source) {
        "JY" -> jingYinUrl(link, link.contains("douyin", ignoreCase = true))
        "AH" -> aHuaUrl(link)
        "EL" -> erLvUrl(link)
        "LX" -> lingXingUrl(link)
        "HS" -> hongShuUrl(link)
        else -> return@withContext null
    }
    val body = HttpUtil.get(apiUrl).getOrNull() ?: return@withContext null
    val json = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return@withContext null
    when (source) {
        "JY" -> parseJingYin(json)
        "AH" -> parseAHua(json)
        "EL" -> parseErLv(json)
        "LX" -> parseLingXing(json)
        "HS" -> parseHongShu(json)
        else -> null
    }
}

internal fun copyText(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("素材链接", text))
    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
}

internal fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
}

@Composable
internal fun ClipboardActionRow(
    onClear: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onClear,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Text("清空")
        }
        FilledTonalButton(
            onClick = onPaste,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Text("粘贴")
        }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showSheet by remember { mutableStateOf(false) }
    var linkInput by rememberSaveable { mutableStateOf("") }
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
        linkInput = link
        result = null
        shareLink = link
        isAnalyzing = true
        scope.launch {
            val res = runCatching { analyzeWith(selectedSource, link) }.getOrNull()
            isAnalyzing = false
            if (res != null && !res.isEmpty) {
                result = res
            } else {
                Toast.makeText(context, "解析失败，请检查链接或稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("素材解析", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_badging_outlined),
                        contentDescription = "通知",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "仅供测试使用，禁止用于非法用途",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showSheet = true },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent,
                ),
                border = BorderStroke(
                    if (showSheet) 2.dp else 1.dp,
                    if (showSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_registration_outlined),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = analyzeSources.firstOrNull { it.key == selectedSource }?.name
                            ?: selectedSource,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (showSheet) {
                focusManager.clearFocus()
                ModalBottomSheet(
                    onDismissRequest = { showSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    dragHandle = { SimpleDragHandle() },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        analyzeSources.forEach { source ->
                            val isSelected = source.key == selectedSource
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSource = source.key
                                        showSheet = false
                                    }
                                    .then(
                                        if (isSelected) Modifier.background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.shapes.medium
                                        ) else Modifier
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = source.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(R.drawable.check_outlined),
                                        contentDescription = "已选择",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = linkInput,
                onValueChange = { linkInput = it },
                label = { Text("分享链接") },
                placeholder = { Text("请输入分享链接") },
                singleLine = false,
                maxLines = 100,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            ClipboardActionRow(
                onClear = {
                    focusManager.clearFocus()
                    linkInput = ""
                },
                onPaste = {
                    focusManager.clearFocus()
                    val pasted = pasteFromClipboard(context)
                    if (pasted != null) {
                        linkInput = pasted
                    } else {
                        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                    }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = ::startAnalyze,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = if (isAnalyzing) "解析中..." else "开始解析",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            result?.let { res ->
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "解析结果",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (res.title.isNotBlank()) {
                    Text(
                        text = res.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (res.imageUrls.isNotEmpty()) {
                        MediaTypeCard(
                            icon = R.drawable.photo_library_outlined,
                            label = "图片",
                            count = res.imageUrls.size,
                            onClick = { onOpenResult("image", res.imageUrls, shareLink, res.title) },
                            trailing = {
                                Icon(
                                    painter = painterResource(R.drawable.pending_outlined),
                                    contentDescription = "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            onClick = { onOpenResult("video", res.videoUrls, shareLink, res.title) },
                            trailing = {
                                Icon(
                                    painter = painterResource(R.drawable.pending_outlined),
                                    contentDescription = "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    IconButton(
                                        onClick = { copyText(context, res.musicUrl) },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.content_copy_outlined),
                                            contentDescription = "复制音乐链接",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = ::downloadMusic,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.cloud_download_outlined),
                                            contentDescription = "下载音乐",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$label（$count）",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
    }
}

