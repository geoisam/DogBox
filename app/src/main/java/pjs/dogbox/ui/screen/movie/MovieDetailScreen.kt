package pjs.dogbox.ui.screen.movie

import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.util.AppData
import pjs.dogbox.util.OkHttpUtil
import pjs.dogbox.util.SharedData
import pjs.dogbox.util.WatchHistoryItem

data class EpisodeInfo(
    val name: String,
    val link: String,
)

data class RouteInfo(
    val name: String,
    val episodes: List<EpisodeInfo>,
)

private data class PageData(
    val routes: List<RouteInfo>,
    val title: String,
    val type: String,
    val actor: String,
    val director: String,
    val description: String,
    val coverUrl: String,
    val m3u8Url: String,
)

private fun extractVideoId(url: String): String {
    val regex = Regex("""/(\d+)[-/]""")
    return regex.find(url)?.groupValues?.getOrNull(1) ?: ""
}

private fun saveWatchHistory(
    context: android.content.Context,
    videoUrl: String,
    title: String,
    coverUrl: String,
    player: ExoPlayer,
) {
    val id = extractVideoId(videoUrl)
    if (id.isEmpty()) return
    val position = player.currentPosition
    if (position < 10000) return
    val duration = player.duration.coerceAtLeast(position)
    val path = try {
        val uri = videoUrl.toUri()
        uri.encodedPath ?: videoUrl
    } catch (_: Exception) {
        videoUrl
    }
    SharedData.addOrUpdateWatchHistory(
        context,
        WatchHistoryItem(
            id = id,
            url = path,
            coverUrl = coverUrl,
            title = title,
            progress = position,
            duration = duration,
            timestamp = System.currentTimeMillis(),
            orientation = 0,
        ),
    )
}

private fun extractPlayerJson(html: String): String? {
    val prefix = "player_aaaa="
    val startIdx = html.indexOf(prefix)
    if (startIdx < 0) return null
    val jsonStart = startIdx + prefix.length
    val braceStart = html.indexOf('{', jsonStart)
    if (braceStart < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in braceStart until html.length) {
        val c = html[i]
        if (escaped) {
            escaped = false
            continue
        }
        if (inString) {
            if (c == '\\') escaped = true
            else if (c == '"') inString = false
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return html.substring(braceStart, i + 1)
                }
            }
        }
    }
    return null
}

private fun parseInitialPage(html: String, baseUrl: String): PageData? {
    return try {
        val doc = Ksoup.parse(html)

        val routes = mutableListOf<RouteInfo>()
        val tabBox = doc.selectFirst("#mobile-tab-box")
        if (tabBox != null) {
            val tabItems = tabBox.select(".module-tab-item")
            val listContents = doc.select(".module-play-list-content")
            for ((index, tabItem) in tabItems.withIndex()) {
                val routeName = tabItem.selectFirst("span")?.text()?.trim() ?: "线路${index + 1}"
                val episodes = mutableListOf<EpisodeInfo>()
                if (index < listContents.size) {
                    val listContent = listContents[index]
                    for (linkEl in listContent.select(".module-play-list-link")) {
                        val href = linkEl.attr("href")
                        val link = if (href.startsWith("http")) href else "$baseUrl$href"
                        val epName = linkEl.selectFirst("span")?.text()?.trim() ?: ""
                        if (epName.isNotEmpty()) {
                            episodes.add(EpisodeInfo(name = epName, link = link))
                        }
                    }
                }
                routes.add(RouteInfo(name = routeName, episodes = episodes))
            }
        }

        if (routes.isEmpty()) {
            val fallbackEpisodes = mutableListOf<EpisodeInfo>()
            for (linkEl in doc.select(".module-play-list-link")) {
                val href = linkEl.attr("href")
                val link = if (href.startsWith("http")) href else "$baseUrl$href"
                val epName = linkEl.selectFirst("span")?.text()?.trim() ?: ""
                if (epName.isNotEmpty()) {
                    fallbackEpisodes.add(EpisodeInfo(name = epName, link = link))
                }
            }
            if (fallbackEpisodes.isNotEmpty()) {
                routes.add(RouteInfo(name = "默认", episodes = fallbackEpisodes))
            }
        }

        val playerMain =
            doc.selectFirst(".player-box-main")?.html() ?: doc.selectFirst("#player-box-main")?.html()
            ?: doc.html()
        val playerJsonStr = extractPlayerJson(playerMain)
        val playerJson =
            playerJsonStr?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val m3u8Url = playerJson?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
        val vodData = playerJson?.get("vod_data")?.jsonObject
        val title = vodData?.get("vod_name")?.jsonPrimitive?.contentOrNull
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "未知影片"
        val type = vodData?.get("vod_class")?.jsonPrimitive?.contentOrNull ?: ""
        val actor = vodData?.get("vod_actor")?.jsonPrimitive?.contentOrNull ?: ""
        val director = vodData?.get("vod_director")?.jsonPrimitive?.contentOrNull ?: ""
        val description =
            doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim() ?: "暂无简介"
        val coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim() ?: ""

        PageData(
            routes = routes,
            title = title,
            type = type,
            actor = actor,
            director = director,
            description = description,
            coverUrl = coverUrl,
            m3u8Url = m3u8Url,
        )
    } catch (_: Exception) {
        null
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    videoUrl: String,
    initialPosition: Long = 0L,
    initialEpisodeUrl: String? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var pageData by remember { mutableStateOf<PageData?>(null) }
    var selectedRouteIndex by remember { mutableIntStateOf(0) }
    var selectedEpisodeIndex by remember { mutableIntStateOf(0) }
    var currentM3u8Url by remember { mutableStateOf("") }
    var currentEpisodeUrl by remember { mutableStateOf(videoUrl) }
    var targetPosition by remember { mutableStateOf(0L) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var fetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isSwitchingEpisode by remember { mutableStateOf(false) }

    var showIntroSheet by remember { mutableStateOf(false) }
    var showRouteSheet by remember { mutableStateOf(false) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    var currentSpeed by remember { mutableStateOf(1.0f) }
    var longPressSpeed by remember { mutableStateOf(3.0f) }
    var currentScaleModeIndex by remember { mutableIntStateOf(0) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showScaleSheet by remember { mutableStateOf(false) }

    val activity = context as? android.app.Activity

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    var playerVideoWidth by remember { mutableIntStateOf(0) }
    var playerVideoHeight by remember { mutableIntStateOf(0) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                playerVideoWidth = videoSize.width
                playerVideoHeight = videoSize.height
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        val act = activity ?: return
        isFullscreen = fullscreen
        val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)

        if (fullscreen) {
            act.requestedOrientation = if (playerVideoWidth > playerVideoHeight)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = isFullscreen) {
        setFullscreen(false)
    }

    val sheetFixedHeight = (LocalConfiguration.current.screenHeightDp.dp - getStatusBarHeight() - 240.dp).coerceAtLeast(300.dp)

    var savedHistoryOnDispose by remember { mutableStateOf(false) }

    fun saveCurrentToHistory() {
        if (savedHistoryOnDispose) return
        savedHistoryOnDispose = true
        saveWatchHistory(context, currentEpisodeUrl, pageData?.title ?: "", pageData?.coverUrl ?: "", exoPlayer)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            saveCurrentToHistory()
            exoPlayer.release()
        }
    }

    LaunchedEffect(targetPosition) {
        if (targetPosition > 0L) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                exoPlayer.seekTo(targetPosition)
                targetPosition = 0L
            } else {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            exoPlayer.seekTo(targetPosition)
                            targetPosition = 0L
                            exoPlayer.removeListener(this)
                        }
                    }
                }
                exoPlayer.addListener(listener)
            }
        }
    }

    val wrappedOnBack = {
        saveCurrentToHistory()
        onBack()
    }

    LaunchedEffect(videoUrl) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            savedHistoryOnDispose = false
            saveCurrentToHistory()
        }
    }

    fun buildBaseUrl(url: String): String {
        val idx = url.indexOf("/", 8)
        return if (idx > 0) url.substring(0, idx) else url
    }

    suspend fun fetchEpisodeM3u8(episodeLink: String): String = withContext(Dispatchers.IO) {
        OkHttpUtil.get(
            episodeLink, mapOf(
                "User-Agent" to AppData.UA_CHROME_PC,
                "Referer" to AppData.NIVOD_SEARCH,
            )
        ).fold(
            onSuccess = { epHtml ->
                val epDoc = Ksoup.parse(epHtml)
                val epPlayerMain = epDoc.selectFirst(".player-box-main")?.html()
                    ?: epDoc.selectFirst("#player-box-main")?.html()
                    ?: epDoc.html()
                val jsonStr = extractPlayerJson(epPlayerMain)
                jsonStr?.let {
                    runCatching {
                        Json.parseToJsonElement(it).jsonObject["url"]?.jsonPrimitive?.content
                    }.getOrNull()
                } ?: ""
            },
            onFailure = { "" },
        )
    }

    fun selectEpisode(routeIdx: Int, epIdx: Int) {
        val data = pageData ?: return
        if (routeIdx < 0 || routeIdx >= data.routes.size) return
        val route = data.routes[routeIdx]
        if (epIdx < 0 || epIdx >= route.episodes.size) return
        fetchJob?.cancel()
        savedHistoryOnDispose = false
        saveCurrentToHistory()
        selectedRouteIndex = routeIdx
        selectedEpisodeIndex = epIdx
        val ep = route.episodes[epIdx]
        currentEpisodeUrl = ep.link
        isSwitchingEpisode = true
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        fetchJob = scope.launch {
            try {
                val m3u8 = fetchEpisodeM3u8(ep.link)
                if (m3u8.isNotEmpty()) {
                    currentM3u8Url = m3u8
                    exoPlayer.apply {
                        stop()
                        setMediaItem(MediaItem.fromUri(m3u8))
                        prepare()
                        playWhenReady = true
                    }
                }
            } catch (_: Exception) {
            } finally {
                isSwitchingEpisode = false
            }
        }
    }

    LaunchedEffect(videoUrl) {
        isLoading = true
        errorMsg = null
        val result = withContext(Dispatchers.IO) {
            OkHttpUtil.get(
                videoUrl, mapOf(
                    "User-Agent" to AppData.UA_CHROME_PC,
                    "Referer" to AppData.NIVOD_SEARCH,
                )
            ).fold(
                onSuccess = { html -> parseInitialPage(html, buildBaseUrl(videoUrl)) },
                onFailure = { null },
            )
        }
        if (result != null && result.routes.isNotEmpty() && result.routes[0].episodes.isNotEmpty()) {
            pageData = result

            val matchUrl = initialEpisodeUrl ?: videoUrl
            val targetPath = try {
                matchUrl.toUri().encodedPath
            } catch (_: Exception) {
                null
            }

            var foundRouteIdx = 0
            var foundEpIdx = 0
            if (targetPath != null) {
                for ((rIdx, route) in result.routes.withIndex()) {
                    for ((eIdx, ep) in route.episodes.withIndex()) {
                        val epPath = try {
                            ep.link.toUri().encodedPath
                        } catch (_: Exception) {
                            null
                        }
                        if (epPath != null && epPath == targetPath) {
                            foundRouteIdx = rIdx
                            foundEpIdx = eIdx
                            break
                        }
                    }
                }
            }

            selectedRouteIndex = foundRouteIdx
            selectedEpisodeIndex = foundEpIdx
            val ep = result.routes[foundRouteIdx].episodes[foundEpIdx]
            currentEpisodeUrl = ep.link

            scope.launch {
                val m3u8 = fetchEpisodeM3u8(ep.link)
                if (m3u8.isNotEmpty()) {
                    currentM3u8Url = m3u8
                    exoPlayer.apply {
                        setMediaItem(MediaItem.fromUri(m3u8))
                        prepare()
                        playWhenReady = true
                    }
                    if (initialPosition > 0L) {
                        targetPosition = initialPosition
                    }
                }
            }
        } else {
            errorMsg = "无法加载视频信息"
        }
        isLoading = false
    }

    val data = pageData
    val currentRoute = data?.routes?.getOrNull(selectedRouteIndex)
    val currentEpisode = currentRoute?.episodes?.getOrNull(selectedEpisodeIndex)

    if (isFullscreen) {
        MoviePlayerScreen(
            exoPlayer = exoPlayer,
            title = pageData?.title,
            episodeName = currentEpisode?.name,
            isFullscreen = true,
            onFullscreenChange = { setFullscreen(it) },
            onBack = wrappedOnBack,
            onNextEpisode = {
                val nextIdx = selectedEpisodeIndex + 1
                if (currentRoute != null && nextIdx < currentRoute.episodes.size) {
                    selectEpisode(selectedRouteIndex, nextIdx)
                } else {
                    Toast.makeText(context, "没有下一集了", Toast.LENGTH_SHORT).show()
                }
            },
            onPreviousEpisode = {
                val prevIdx = selectedEpisodeIndex - 1
                if (prevIdx >= 0) {
                    selectEpisode(selectedRouteIndex, prevIdx)
                } else {
                    Toast.makeText(context, "没有上一集了", Toast.LENGTH_SHORT).show()
                }
            },
            episodes = currentRoute?.episodes ?: emptyList(),
            selectedEpisodeIndex = selectedEpisodeIndex,
            onEpisodeSelect = { idx ->
                if (idx != selectedEpisodeIndex) {
                    selectEpisode(selectedRouteIndex, idx)
                }
            },
            isSwitchingEpisode = isSwitchingEpisode,
            scaleModeIndex = currentScaleModeIndex,
            onScaleModeIndexChange = { currentScaleModeIndex = it },
            modifier = modifier,
        )
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) { paddingValues ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    ChrysanthemumLoading()
                }
            } else if (errorMsg != null || pageData == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(errorMsg ?: "未知错误", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val data = pageData!!
                val currentRoute = data.routes.getOrNull(selectedRouteIndex) ?: return@Scaffold
                val totalRoutes = data.routes.size
                val totalEpisodes = currentRoute.episodes.size

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 0.dp)
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
                ) {

                    MoviePlayerScreen(
                        exoPlayer = exoPlayer,
                        title = pageData?.title,
                        isFullscreen = false,
                        onFullscreenChange = { setFullscreen(it) },
                        onBack = wrappedOnBack,
                        isSwitchingEpisode = isSwitchingEpisode,
                        scaleModeIndex = currentScaleModeIndex,
                        onScaleModeIndexChange = { currentScaleModeIndex = it },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, data.coverUrl.toUri())
                                        context.startActivity(intent)
                                    },
                                ),
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(data.coverUrl)
                                        .crossfade(true)
                                        .httpHeaders(
                                            NetworkHeaders.Builder()
                                                .set("Referer", AppData.NIVOD_SEARCH)
                                                .set("User-Agent", AppData.UA_CHROME_M)
                                                .build()
                                        ).build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 100.dp, height = 140.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier
                                    .height(140.dp)
                                    .weight(1f)
                                    .clickable { showIntroSheet = true },
                            ) {
                                Text(
                                    text = data.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "类型：${data.type}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "导演：${data.director}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "主演：${data.actor}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "简介：${data.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ActionIconItem(
                                icon = R.drawable.link_outlined,
                                label = "复制链接",
                                onClick = {
                                    val clipper =
                                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                                as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText(
                                        "link",
                                        currentM3u8Url
                                    )
                                    clipper.setPrimaryClip(clip)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.kid_star_outlined,
                                label = "收藏视频",
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.cloud_download_outlined,
                                label = "缓存视频",
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.speed_outlined,
                                label = "播放倍速",
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.aspect_ratio_outlined,
                                label = "视频画幅",
                                onClick = { showScaleSheet = true },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "线路",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "正在使用：${currentRoute.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier
                                    .clickable { showRouteSheet = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${totalRoutes}个线路",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            data.routes.forEachIndexed { index, route ->
                                FilterChip(
                                    selected = index == selectedRouteIndex,
                                    onClick = {
                                        if (index != selectedRouteIndex) {
                                            selectEpisode(index, 0)
                                        }
                                    },
                                    label = { Text(route.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val currentEpisode = currentRoute.episodes.getOrNull(selectedEpisodeIndex)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "选集",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "正在播放：${currentEpisode?.name ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier
                                    .clickable { showEpisodeSheet = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${totalEpisodes}个视频",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            currentRoute.episodes.forEachIndexed { index, ep ->
                                FilterChip(
                                    selected = index == selectedEpisodeIndex,
                                    onClick = {
                                        if (index != selectedEpisodeIndex) {
                                            selectEpisode(selectedRouteIndex, index)
                                        }
                                    },
                                    label = { Text(ep.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showIntroSheet && pageData != null) {
        IntroBottomSheet(
            data = pageData!!,
            onDismiss = { showIntroSheet = false },
            fixedHeight = sheetFixedHeight,
        )
    }

    if (showRouteSheet && pageData != null) {
        RouteBottomSheet(
            routes = pageData!!.routes,
            selectedIndex = selectedRouteIndex,
            onSelect = { idx ->
                if (idx != selectedRouteIndex) {
                    selectEpisode(idx, 0)
                }
                showRouteSheet = false
            },
            onDismiss = { showRouteSheet = false },
            fixedHeight = sheetFixedHeight,
        )
    }

    if (showEpisodeSheet && pageData != null) {
        EpisodeBottomSheet(
            episodes = pageData!!.routes.getOrNull(selectedRouteIndex)?.episodes ?: emptyList(),
            selectedIndex = selectedEpisodeIndex,
            onSelect = { idx ->
                if (idx != selectedEpisodeIndex) {
                    selectEpisode(selectedRouteIndex, idx)
                }
                showEpisodeSheet = false
            },
            onDismiss = { showEpisodeSheet = false },
            fixedHeight = sheetFixedHeight,
        )
    }

    if (showSpeedSheet) {
        SpeedBottomSheet(
            currentSpeed = currentSpeed,
            longPressSpeed = longPressSpeed,
            onSpeedSelect = { speed ->
                currentSpeed = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            },
            onLongPressSpeedConfirm = { speed ->
                longPressSpeed = speed
            },
            onDismiss = { showSpeedSheet = false },
            fixedHeight = sheetFixedHeight,
        )
    }

    if (showScaleSheet) {
        ScaleBottomSheet(
            selectedIndex = currentScaleModeIndex,
            onSelect = { index ->
                currentScaleModeIndex = index
            },
            onDismiss = { showScaleSheet = false },
            fixedHeight = sheetFixedHeight,
        )
    }
}

@Composable
private fun ActionIconItem(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntroBottomSheet(
    data: PageData,
    onDismiss: () -> Unit,
    fixedHeight: Dp,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedHeight),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "详情",
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close_outlined),
                        contentDescription = "关闭",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
                    .padding(horizontal = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (data.type.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = data.type,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "简介",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (data.director.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "导演",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = data.director,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (data.actor.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "主演",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = data.actor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RouteBottomSheet(
    routes: List<RouteInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    fixedHeight: Dp,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedHeight),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "线路",
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close_outlined),
                        contentDescription = "关闭",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    routes.forEachIndexed { index, route ->
                        FilterChip(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            label = { Text(route.name) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeBottomSheet(
    episodes: List<EpisodeInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    fixedHeight: Dp,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pageSize = 100
    val totalEpisodes = episodes.size
    val categoryCount = (totalEpisodes + pageSize - 1) / pageSize
    val hasCategories = categoryCount > 1

    val pagerState = rememberPagerState(
        pageCount = { categoryCount.coerceAtLeast(1) },
        initialPage = if (hasCategories) selectedIndex / pageSize else 0,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedHeight),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选集",
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close_outlined),
                        contentDescription = "关闭",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (hasCategories) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    divider = {},
                    edgePadding = 0.dp,
                ) {
                    (0 until categoryCount).forEach { catIdx ->
                        val start = catIdx * pageSize + 1
                        val end = minOf((catIdx + 1) * pageSize, totalEpisodes)
                        Tab(
                            selected = catIdx == pagerState.currentPage,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(catIdx)
                                }
                            },
                            text = { Text("$start-$end") },
                        )
                    }
                }
            }

            if (hasCategories) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    val startIdx = page * pageSize
                    val endIdx = minOf(startIdx + pageSize, totalEpisodes)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .wrapContentWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            episodes.subList(startIdx, endIdx).forEachIndexed { idx, ep ->
                                val realIdx = startIdx + idx
                                FilterChip(
                                    selected = realIdx == selectedIndex,
                                    onClick = { onSelect(realIdx) },
                                    label = { Text(ep.name) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        episodes.forEachIndexed { index, ep ->
                            FilterChip(
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) },
                                label = { Text(ep.name) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
