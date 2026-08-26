package pjs.dogbox.ui.screen.movie

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableChipColors
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import androidx.compose.material3.CircularProgressIndicator
import pjs.dogbox.ui.components.CoverImage
import pjs.dogbox.ui.navigation.DisposableState
import pjs.dogbox.data.AppData
import pjs.dogbox.utils.AdsFilter
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.data.SharedData
import pjs.dogbox.ui.components.SimpleDragHandle
import pjs.dogbox.data.WatchHistoryItem
import pjs.dogbox.ui.components.SheetSectionTitle
import kotlin.math.roundToInt

data class EpisodeInfo(
    val name: String,
    val link: String,
)

data class RouteInfo(
    val name: String,
    val episodes: List<EpisodeInfo>,
)

internal data class PageData(
    val routes: List<RouteInfo>,
    val title: String,
    val type: String,
    val actor: String,
    val director: String,
    val description: String,
    val coverUrl: String,
    val m3u8Url: String,
)

internal class MovieDetailScreenState(
    private val appContext: android.content.Context,
    videoUrl: String,
    initialPosition: Long,
    initialEpisodeUrl: String?,
) : DisposableState {
    val initialPosition: Long = initialPosition
    val initialEpisodeUrl: String? = initialEpisodeUrl

    val isLoading = mutableStateOf(true)
    val pageData = mutableStateOf<PageData?>(null)
    val selectedRouteIndex = mutableIntStateOf(0)
    val selectedEpisodeIndex = mutableIntStateOf(0)
    val currentM3u8Url = mutableStateOf("")
    val currentEpisodeUrl = mutableStateOf(videoUrl)
    val targetPosition = mutableStateOf(0L)
    val errorMsg = mutableStateOf<String?>(null)
    val retryKey = mutableIntStateOf(0)
    val fetchJob = mutableStateOf<kotlinx.coroutines.Job?>(null)
    val isSwitchingEpisode = mutableStateOf(false)
    val pendingSwitchTarget = mutableStateOf<Pair<Int, Int>?>(null)
    val pendingSwitchBack = mutableStateOf<(() -> Unit)?>(null)

    val showIntroSheet = mutableStateOf(false)
    val showRouteSheet = mutableStateOf(false)
    val showEpisodeSheet = mutableStateOf(false)
    val isFullscreen = mutableStateOf(false)

    val currentSpeed = mutableStateOf(1.0f)
    val currentScaleModeIndex = mutableIntStateOf(0)
    val showSpeedSheet = mutableStateOf(false)
    val showScaleSheet = mutableStateOf(false)

    val playerVideoWidth = mutableIntStateOf(0)
    val playerVideoHeight = mutableIntStateOf(0)
    val wasPlayingBeforePause = mutableStateOf(false)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build()

    fun saveCurrentToHistory() {
        val epName = pageData.value?.routes?.getOrNull(selectedRouteIndex.value)
            ?.episodes?.getOrNull(selectedEpisodeIndex.value)?.name ?: ""
        saveWatchHistory(
            appContext,
            currentEpisodeUrl.value,
            pageData.value?.title ?: "",
            pageData.value?.coverUrl ?: "",
            epName,
            exoPlayer,
        )
    }

    override fun dispose() {
        saveCurrentToHistory()
        SharedData.flushWatchHistory(appContext)
        exoPlayer.release()
    }
}

internal fun extractVideoId(url: String): String {
    val uri = url.toUri()
    val last = uri.pathSegments.lastOrNull() ?: return ""
    return last.substringBefore(".").substringBefore("-")
}

internal fun extractHost(url: String): String {
    val idx = url.indexOf("/", 8)
    return if (idx > 0) url.substring(0, idx) else url
}

private fun saveWatchHistory(
    context: android.content.Context,
    videoUrl: String,
    title: String,
    coverUrl: String,
    episodeName: String,
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
            episodeName = episodeName,
            host = extractHost(videoUrl),
            key = AppData.sourceKey(extractHost(videoUrl)),
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
            doc.selectFirst(".player-box-main")?.html() ?: doc.selectFirst("#player-box-main")
                ?.html()
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
internal fun MovieDetailScreen(
    state: MovieDetailScreenState,
    videoUrl: String,
    onBack: () -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by state.isLoading
    var pageData by state.pageData
    var selectedRouteIndex by state.selectedRouteIndex
    var selectedEpisodeIndex by state.selectedEpisodeIndex
    var currentM3u8Url by state.currentM3u8Url
    var currentEpisodeUrl by state.currentEpisodeUrl
    var targetPosition by state.targetPosition
    var errorMsg by state.errorMsg
    var retryKey by state.retryKey
    var fetchJob by state.fetchJob
    var isSwitchingEpisode by state.isSwitchingEpisode
    var pendingSwitchTarget by state.pendingSwitchTarget
    var pendingSwitchBack by state.pendingSwitchBack

    var showIntroSheet by state.showIntroSheet
    var showRouteSheet by state.showRouteSheet
    var showEpisodeSheet by state.showEpisodeSheet
    var isFullscreen by state.isFullscreen
    var showCopySheet by remember { mutableStateOf(false) }

    var currentSpeed by state.currentSpeed
    var currentScaleModeIndex by state.currentScaleModeIndex
    var showSpeedSheet by state.showSpeedSheet
    var showScaleSheet by state.showScaleSheet

    val activity = context as? android.app.Activity

    val exoPlayer = state.exoPlayer

    var playerVideoWidth by state.playerVideoWidth
    var playerVideoHeight by state.playerVideoHeight

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                playerVideoWidth = videoSize.width
                playerVideoHeight = videoSize.height
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    pendingSwitchBack = null
                    pendingSwitchTarget = null
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (pendingSwitchTarget != null && pendingSwitchBack != null &&
                    pendingSwitchTarget == (selectedRouteIndex to selectedEpisodeIndex)
                ) {
                    val action = pendingSwitchBack
                    pendingSwitchBack = null
                    pendingSwitchTarget = null
                    action?.invoke()
                }
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

    val saveCurrentToHistory = { state.saveCurrentToHistory() }

    fun copyToClipboard(text: String) {
        val clipper = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipper.setPrimaryClip(ClipData.newPlainText("链接", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    fun openInBrowser(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show() }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            state.wasPlayingBeforePause.value = exoPlayer.playWhenReady
            exoPlayer.pause()
        }
    }

    LaunchedEffect(Unit) {
        if (!state.isFullscreen.value && state.wasPlayingBeforePause.value) {
            exoPlayer.play()
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

    BackHandler {
        if (isFullscreen) {
            setFullscreen(false)
        } else {
            wrappedOnBack()
        }
    }

    LaunchedEffect(videoUrl) {
        while (true) {
            kotlinx.coroutines.delay(15000)
            saveCurrentToHistory()
        }
    }

    fun buildBaseUrl(url: String): String = extractHost(url)

    suspend fun fetchEpisodeM3u8(episodeLink: String): String = withContext(Dispatchers.IO) {
        HttpUtil.get(
            episodeLink, mapOf(
                "User-Agent" to AppData.UA_CHROME_PC,
                "Referer" to buildBaseUrl(videoUrl),
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

    suspend fun filterAds(url: String): String =
        if (url.isNotEmpty()) AdsFilter.filter(context, url, GlobalPlayerConfig.adsFilter) else url

    fun selectEpisode(routeIdx: Int, epIdx: Int) {
        val data = pageData ?: return
        if (routeIdx < 0 || routeIdx >= data.routes.size) return
        val route = data.routes[routeIdx]
        if (epIdx < 0 || epIdx >= route.episodes.size) return
        val prevRouteIdx = selectedRouteIndex
        val prevEpIdx = selectedEpisodeIndex
        val prevEpisodeUrl = currentEpisodeUrl
        val prevM3u8Url = currentM3u8Url
        fetchJob?.cancel()
        saveCurrentToHistory()
        selectedRouteIndex = routeIdx
        selectedEpisodeIndex = epIdx
        val ep = route.episodes[epIdx]
        currentEpisodeUrl = ep.link
        isSwitchingEpisode = true
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        pendingSwitchTarget = routeIdx to epIdx
        pendingSwitchBack = {
            selectedRouteIndex = prevRouteIdx
            selectedEpisodeIndex = prevEpIdx
            currentEpisodeUrl = prevEpisodeUrl
            currentM3u8Url = prevM3u8Url
            isSwitchingEpisode = false
            if (prevM3u8Url.isNotEmpty()) {
                exoPlayer.apply {
                    stop()
                    setMediaItem(MediaItem.fromUri(prevM3u8Url))
                    prepare()
                    playWhenReady = true
                }
            }
        }
        fetchJob = scope.launch {
            try {
                val m3u8 = filterAds(fetchEpisodeM3u8(ep.link))
                if (m3u8.isNotEmpty()) {
                    currentM3u8Url = m3u8
                    exoPlayer.apply {
                        stop()
                        setMediaItem(MediaItem.fromUri(m3u8))
                        prepare()
                        playWhenReady = true
                    }
                } else {
                    val action = pendingSwitchBack
                    pendingSwitchBack = null
                    pendingSwitchTarget = null
                    action?.invoke()
                }
            } catch (_: Exception) {
                val action = pendingSwitchBack
                pendingSwitchBack = null
                pendingSwitchTarget = null
                action?.invoke()
            } finally {
                isSwitchingEpisode = false
            }
        }
    }

    LaunchedEffect(videoUrl, retryKey) {
        if (pageData != null && currentM3u8Url.isNotEmpty()) return@LaunchedEffect
        isLoading = true
        errorMsg = null
        val result = withContext(Dispatchers.IO) {
            HttpUtil.get(
                videoUrl, mapOf(
                    "User-Agent" to AppData.UA_CHROME_PC,
                    "Referer" to buildBaseUrl(videoUrl),
                )
            ).fold(
                onSuccess = { html -> parseInitialPage(html, buildBaseUrl(videoUrl)) },
                onFailure = { null },
            )
        }
        if (result != null && result.routes.isNotEmpty() && result.routes[0].episodes.isNotEmpty()) {
            pageData = result

            val matchUrl = state.initialEpisodeUrl ?: videoUrl
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
                val m3u8 = filterAds(fetchEpisodeM3u8(ep.link))
                if (m3u8.isNotEmpty()) {
                    currentM3u8Url = m3u8
                    exoPlayer.apply {
                        setMediaItem(MediaItem.fromUri(m3u8))
                        prepare()
                        playWhenReady = true
                    }
                    if (state.initialPosition > 0L) {
                        targetPosition = state.initialPosition
                    }
                } else {
                    errorMsg = "无法加载播放地址"
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
            if (errorMsg != null || pageData == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    PlayerPlaceholder(
                        onBack = wrappedOnBack,
                        isLoading = isLoading,
                        onRetry = if (!isLoading) ({ retryKey++ }) else null,
                    )
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

                    if (currentM3u8Url.isNotEmpty()) {
                        VideoPlayerScreen(
                            exoPlayer = exoPlayer,
                            title = data.title,
                            isVod = true,
                            isFullscreen = false,
                            onFullscreenChange = { setFullscreen(it) },
                            onBack = wrappedOnBack,
                            scaleModeIndex = currentScaleModeIndex,
                            onScaleModeIndexChange = { currentScaleModeIndex = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PlayerPlaceholder(
                            onBack = wrappedOnBack,
                            isLoading = true,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showIntroSheet = true },
                        ) {
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        onViewImage(data.coverUrl, buildBaseUrl(videoUrl), data.title)
                                    },
                                ),
                            ) {
                                CoverImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(data.coverUrl)
                                        .crossfade(true)
                                        .httpHeaders(
                                            NetworkHeaders.Builder()
                                                .set("Referer", buildBaseUrl(videoUrl))
                                                .build()
                                        ).build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 100.dp, height = 140.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer
                                                .copy(alpha = 0.9f),
                                            shape = MaterialTheme.shapes.medium,
                                        )
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = AppData.sourceName(AppData.sourceKey(extractHost(videoUrl))),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier
                                    .height(140.dp)
                                    .weight(1f),
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
                                onClick = { showCopySheet = true },
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

                        ChipScrollRow(
                            labels = data.routes.map { it.name },
                            selectedIndex = selectedRouteIndex,
                            onSelect = { index ->
                                if (index != selectedRouteIndex) {
                                    selectEpisode(index, 0)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )

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

                        ChipScrollRow(
                            labels = currentRoute.episodes.map { it.name },
                            selectedIndex = selectedEpisodeIndex,
                            labelKey = selectedRouteIndex,
                            onSelect = { index ->
                                if (index != selectedEpisodeIndex) {
                                    selectEpisode(selectedRouteIndex, index)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showCopySheet) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val sheetMaxHeight =
            (LocalConfiguration.current.screenHeightDp.dp - statusBarTop - 240.dp - 20.dp)
        val copySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCopySheet = false },
            sheetState = copySheetState,
            dragHandle = { SimpleDragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetMaxHeight)
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pageData?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = AppData.sourceName(AppData.sourceKey(extractHost(videoUrl))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                SheetActionItem(
                    icon = R.drawable.link_outlined,
                    label = "复制播放链接",
                    onClick = {
                        showCopySheet = false
                        copyToClipboard(currentM3u8Url)
                    },
                )
                SheetActionItem(
                    icon = R.drawable.content_copy_outlined,
                    label = "复制网页地址",
                    onClick = {
                        showCopySheet = false
                        copyToClipboard(currentEpisodeUrl)
                    },
                )
                SheetActionItem(
                    icon = R.drawable.open_in_browser_outlined,
                    label = "浏览器打开播放链接",
                    onClick = {
                        showCopySheet = false
                        openInBrowser(currentM3u8Url)
                    },
                )
                SheetActionItem(
                    icon = R.drawable.open_in_browser_outlined,
                    label = "浏览器打开网页链接",
                    onClick = {
                        showCopySheet = false
                        openInBrowser(currentEpisodeUrl)
                    },
                )
            }
        }
    }

    if (showIntroSheet && pageData != null) {
        IntroBottomSheet(
            data = pageData!!,
            onDismiss = { showIntroSheet = false },
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
        )
    }

    if (showSpeedSheet) {
        SpeedBottomSheet(
            currentSpeed = currentSpeed,
            onSpeedSelect = { speed ->
                currentSpeed = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            },
            onDismiss = { showSpeedSheet = false },
        )
    }

    if (showScaleSheet) {
        ScaleBottomSheet(
            selectedIndex = currentScaleModeIndex,
            onSelect = { index ->
                currentScaleModeIndex = index
            },
            onDismiss = { showScaleSheet = false },
        )
    }
}

@Composable
private fun PlayerPlaceholder(
    onBack: () -> Unit,
    isLoading: Boolean = true,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color.Black),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 2.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back_outlined),
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(56.dp))
                onRetry != null -> Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onRetry() }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "点击重试",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                else -> Icon(
                    painter = painterResource(R.drawable.autoplay_outlined),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun ChipScrollRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    colors: SelectableChipColors? = null,
    labelKey: Any? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val itemXs = remember(labels.size, labelKey) { IntArray(labels.size) { Int.MIN_VALUE } }
    val itemWidths = remember(labels.size, labelKey) { IntArray(labels.size) { 0 } }

    LaunchedEffect(selectedIndex, labelKey, labels.size, rowWidthPx) {
        if (selectedIndex !in labels.indices) return@LaunchedEffect
        if (rowWidthPx <= 0) return@LaunchedEffect
        var itemX = itemXs.getOrNull(selectedIndex) ?: return@LaunchedEffect
        var attempts = 0
        while (itemX == Int.MIN_VALUE && attempts < 30) {
            withFrameNanos { }
            attempts++
            itemX = itemXs.getOrNull(selectedIndex) ?: return@LaunchedEffect
        }
        if (itemX == Int.MIN_VALUE) return@LaunchedEffect
        val itemW = itemWidths.getOrNull(selectedIndex) ?: return@LaunchedEffect
        val current = scrollState.value
        val left = itemX - current
        val right = left + itemW
        if (left >= 0 && right <= rowWidthPx) return@LaunchedEffect
        val target = if (left < 0) itemX else itemX + itemW - rowWidthPx
        val clamped = target.coerceIn(0, scrollState.maxValue)
        if (clamped != current) scrollState.animateScrollTo(clamped)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label) },
                modifier = Modifier.onGloballyPositioned { coords ->
                    itemXs[index] = coords.positionInParent().x.roundToInt()
                    itemWidths[index] = coords.size.width
                },
                colors = colors ?: FilterChipDefaults.filterChipColors(),
            )
        }
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

@Composable
private fun SheetActionItem(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntroBottomSheet(
    data: PageData,
    onDismiss: () -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp.dp - statusBarTop - 240.dp - 20.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SimpleDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetMaxHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SheetSectionTitle("简介详情")
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 32.dp),
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
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RouteBottomSheet(
    routes: List<RouteInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp.dp - statusBarTop - 240.dp - 20.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedIndex) {
        withFrameNanos { }
        repeat(10) {
            bringIntoViewRequester.bringIntoView()
            withFrameNanos { }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SimpleDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetMaxHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SheetSectionTitle("线路")
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    routes.forEachIndexed { index, route ->
                        PlayerFilterChip(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            label = route.name,
                            useFixedDarkColors = false,
                            modifier = if (index == selectedIndex) {
                                Modifier.bringIntoViewRequester(bringIntoViewRequester)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp.dp - statusBarTop - 240.dp - 20.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SimpleDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetMaxHeight),
        ) {
            SheetSectionTitle("选集")
            EpisodePanelContent(
                episodes = episodes,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                useFixedDarkColors = false,
            )
        }
    }
}