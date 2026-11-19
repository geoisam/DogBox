package pjs.dogbox.ui.screens.movie

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.data.repository.ApiSitesRepository
import pjs.dogbox.data.repository.AppRepository
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.ui.components.CoverImage
import pjs.dogbox.ui.navigation.DisposableState
import pjs.dogbox.utils.ads.AdsFilter
import pjs.dogbox.utils.network.HttpUtil
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.MoreCircle
import top.yukonga.miuix.kmp.icon.glass.PlaybackSpeed
import top.yukonga.miuix.kmp.icon.glass.ScreenSize
import top.yukonga.miuix.kmp.icon.glass.StarOutline
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

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

@androidx.annotation.OptIn(UnstableApi::class)
internal class MovieDetailScreenState(
    private val appContext: Context,
    videoUrl: String,
    val initialPosition: Long,
    val initialEpisodeUrl: String?,
) : DisposableState {

    val isLoading = mutableStateOf(true)
    val pageData = mutableStateOf<PageData?>(null)
    val selectedRouteIndex = mutableIntStateOf(0)
    val selectedEpisodeIndex = mutableIntStateOf(0)
    val currentM3u8Url = mutableStateOf("")
    val currentEpisodeUrl = mutableStateOf(videoUrl)
    val targetPosition = mutableStateOf(0L)
    val errorMsg = mutableStateOf<String?>(null)
    val retryKey = mutableIntStateOf(0)
    val fetchJob = mutableStateOf<Job?>(null)
    val isSwitchingEpisode = mutableStateOf(false)
    val pendingSwitchTarget = mutableStateOf<Pair<Int, Int>?>(null)
    val pendingSwitchBack = mutableStateOf<(() -> Unit)?>(null)
    val playErrorMsg = mutableStateOf<String?>(null)
    val isPlayLoading = mutableStateOf(false)
    val playRetryKey = mutableIntStateOf(0)
    // 重载当前集后需要恢复到的播放位置（0 表示不需要），由广告过滤开关切换触发
    val pendingResumePosition = mutableStateOf(0L)

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

    private val controllerFuture: ListenableFuture<MediaController> = connectMediaController(appContext)
    private val mediaController = mutableStateOf<MediaController?>(null)

    /**
     * 播放器由 MediaPlaybackService 持有，并通过 MediaSession 暴露给系统媒体控制器。
     * 服务连接未就绪时为 null，UI 此时显示加载占位。
     */
    val exoPlayer: Player? get() = mediaController.value

    // videoUrl 即详情页链接，用于生成 id（详情页 path）
    val detailUrl: String = videoUrl

    init {
        // 预加载 sites.json，使 AppData key/host 映射对 API 站点生效
        runCatching { ApiSitesRepository.ensureLoaded(appContext) }
        controllerFuture.addListener({
            try {
                mediaController.value = controllerFuture.get()
            } catch (_: Exception) {
            }
        }, appContext.mainExecutor)
    }

    fun saveCurrentToHistory() {
        val controller = exoPlayer ?: return
        val epName = pageData.value?.routes?.getOrNull(selectedRouteIndex.value)
            ?.episodes?.getOrNull(selectedEpisodeIndex.value)?.name ?: ""
        saveWatchHistory(
            appContext,
            detailUrl,
            currentEpisodeUrl.value,
            pageData.value?.title ?: "",
            pageData.value?.coverUrl ?: "",
            epName,
            controller,
        )
    }

    override fun dispose() {
        saveCurrentToHistory()
        AppRepository.flushWatchHistory(appContext)
        val controller = mediaController.value
        if (controller != null) {
            if (!GlobalPlayerConfig.backgroundPlayback) {
                controller.pause()
            }
            runCatching { controller.release() }
        }
        MediaController.releaseFuture(controllerFuture)
    }
}

internal fun extractHost(url: String): String {
    val idx = url.indexOf("/", 8)
    return if (idx > 0) url.substring(0, idx) else url
}

internal fun extractPath(url: String): String {
    return try {
        val p = url.toUri().encodedPath
        if (!p.isNullOrEmpty()) p else url
    } catch (_: Exception) {
        url
    }
}

internal fun extractPathWithQuery(url: String): String {
    return try {
        val uri = url.toUri()
        val path = uri.encodedPath ?: ""
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        val result = path + query
        result.ifEmpty { url }
    } catch (_: Exception) {
        url
    }
}

private fun saveWatchHistory(
    context: Context,
    detailUrl: String,
    episodeUrl: String,
    title: String,
    coverUrl: String,
    episodeName: String,
    player: Player,
) {
    runCatching { ApiSitesRepository.ensureLoaded(context) }
    val detailPath = extractPathWithQuery(detailUrl).takeIf { it.isNotBlank() } ?: return
    // 规范化：确保以 / 开头（API 播放地址为完整直链则原样保留）
    val id = if (detailPath.startsWith("/")) detailPath else "/$detailPath"
    if (id.isEmpty() || id == "/") return
    val position = player.currentPosition
    if (position < 10000) return
    val duration = player.duration.coerceAtLeast(position)
    val host = extractHost(detailUrl)
    // API 源选集即完整直链，原样记录；其余情况只记录 path
    val epPathRaw = if (episodeUrl.startsWith("http")) episodeUrl else extractPath(episodeUrl)
    val epPath =
        if (epPathRaw.startsWith("/") || epPathRaw.startsWith("http")) epPathRaw else "/$epPathRaw"
    val key = AppConfig.sourceKey(host)
    // coverUrl 存完整链接（host 可能与 key 的 host 不同，不能仅存 path）
    val coverFull = coverUrl
    AppRepository.addOrUpdateWatchHistory(
        context,
        AppRepository.WatchHistoryItem(
            id = id,
            url = epPath,
            coverUrl = coverFull,
            title = title,
            progress = position,
            duration = duration,
            timestamp = System.currentTimeMillis(),
            orientation = 0,
            episodeName = episodeName,
            key = key,
        ),
    )
}

private fun isBlockedVideoUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return listOf(
        "v.qq.com", "v.youku.com", "www.iqiyi.com", "www.mgtv.com", "www.bilibili.com"
    ).any { blocked -> lower.contains(blocked) }
}

private fun isDirectVideoUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    // 直接可播放的后缀（忽略大小写、忽略 ? 后参数）
    // 包含 .m3u8 / .mp4 等即认为可直接播放
    val directExts = listOf(
        ".m3u8", ".mp4", ".m4v", ".mov", ".avi", ".mkv",
        ".flv", ".wmv", ".webm", ".ts", ".mpd", ".3gp", ".m3u"
    )
    return directExts.any { ext -> lower.contains(ext) }
}

private suspend fun resolveIndirectVideoUrl(intermediateUrl: String, referer: String): String {
    if (intermediateUrl.isBlank()) return ""
    // 被认为是间接链接（如 /share/...），需二次请求页面源码
    val htmlResult = HttpUtil.get(
        intermediateUrl,
        mapOf(
            "User-Agent" to AppConfig.UA_CHROME_PC,
            "Referer" to referer,
        )
    )
    val html = htmlResult.getOrNull() ?: return ""
    // 需求：用正则匹配 "" 里面包含 .m3u8 的即为真实播放路径
    // 先匹配双引号，再兜底单引号
    var path: String? = Regex("\"([^\"]*\\.m3u8[^\"]*)\"")
        .find(html)?.groupValues?.getOrNull(1)
    if (path.isNullOrBlank()) {
        path = Regex("'([^']*\\.m3u8[^']*)'")
            .find(html)?.groupValues?.getOrNull(1)
    }
    if (path.isNullOrBlank()) return ""
    // 处理转义：如 \/ -> /
    path = path.replace("\\/", "/").replace("\\\"", "\"").trim()
    if (path.isBlank()) return ""
    // 真实播放链接的 host 就是请求的那个链接的 host
    val host = extractHost(intermediateUrl)
    return when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> host + path
        else -> "$host/$path"
    }
}

private val vodDetailJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun stripVodHtml(raw: String): String {
    if (raw.isBlank()) return ""
    return raw.replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()
}

/**
 * CMS JSON 接口详情解析（sites.json 片源）。
 * vod_name→标题；vod_year(英文逗号分割)+vod_class→类型；vod_pic→封面；
 * vod_actor→主演；vod_director→导演；vod_content→简介；vod_play_url→播放链接。
 * vod_play_url：多来源以 $$$ 分隔（对应 vod_play_from），组内以 # 分集，每集 "名称$地址"。
 */
private fun parseApiDetail(json: String): PageData? {
    return try {
        val root = vodDetailJson.parseToJsonElement(json).jsonObject
        val arr = root["list"] as? JsonArray ?: return null
        val o = arr.firstOrNull() as? JsonObject ?: return null
        fun str(name: String): String = o[name]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
        val title = str("vod_name").takeIf { it.isNotBlank() } ?: "未知"
        // 类型：vod_year 按英文逗号分割，加上 vod_class
        val yearParts = str("vod_year").split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        val classParts =
            str("vod_class").split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        val type = (yearParts + classParts).joinToString(",").takeIf { it.isNotBlank() } ?: "未知"
        val coverRaw = str("vod_pic")
        val coverUrl = when {
            coverRaw.startsWith("http") -> coverRaw
            coverRaw.startsWith("//") -> "https:$coverRaw"
            coverRaw.isNotBlank() -> coverRaw
            else -> "未知"
        }
        val actor = str("vod_actor").takeIf { it.isNotBlank() } ?: "未知"
        val director = str("vod_director").takeIf { it.isNotBlank() } ?: "未知"
        val description = stripVodHtml(str("vod_content")).takeIf { it.isNotBlank() } ?: "未知"

        val froms = str("vod_play_from").split("$$$").map { it.trim() }
        val groups = str("vod_play_url").split("$$$")
        val routes = mutableListOf<RouteInfo>()
        for ((gi, group) in groups.withIndex()) {
            if (group.isBlank()) continue
            val routeName = froms.getOrNull(gi)?.takeIf { it.isNotBlank() } ?: "线路${gi + 1}"
            val episodes = mutableListOf<EpisodeInfo>()
            for ((ei, seg) in group.split("#").withIndex()) {
                val s = seg.trim()
                if (s.isBlank()) continue
                val dollar = s.indexOf("$")
                val epName: String
                val epLink: String
                if (dollar < 0) {
                    epName = "第${ei + 1}集"
                    epLink = s
                } else {
                    epName =
                        s.substring(0, dollar).trim().takeIf { it.isNotEmpty() } ?: "第${ei + 1}集"
                    epLink = s.substring(dollar + 1).trim()
                }
                if (epLink.isBlank()) continue
                episodes.add(EpisodeInfo(name = epName, link = epLink))
            }
            if (episodes.isNotEmpty()) routes.add(RouteInfo(name = routeName, episodes = episodes))
        }
        val m3u8Url = routes.firstOrNull()?.episodes?.firstOrNull()?.link ?: ""
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
    var playErrorMsg by state.playErrorMsg
    var isPlayLoading by state.isPlayLoading
    var playRetryKey by state.playRetryKey
    var pendingResumePosition by state.pendingResumePosition

    var showIntroSheet by state.showIntroSheet
    var showRouteSheet by state.showRouteSheet
    var showEpisodeSheet by state.showEpisodeSheet
    var isFullscreen by state.isFullscreen
    var showMoreSheet by remember { mutableStateOf(false) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    var currentSpeed by state.currentSpeed
    var currentScaleModeIndex by state.currentScaleModeIndex
    var showSpeedSheet by state.showSpeedSheet
    var showScaleSheet by state.showScaleSheet

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetMaxHeight =
        LocalConfiguration.current.screenHeightDp.dp - statusBarTop - 240.dp - 64.dp

    val activity = context as? Activity

    var playerVideoWidth by state.playerVideoWidth
    var playerVideoHeight by state.playerVideoHeight

    // 媒体服务连接未就绪时显示加载占位，连接完成后重组继续
    val exoPlayer: Player = state.exoPlayer ?: run {
        PlayerPlaceholder(onBack = onBack, isLoading = true)
        return
    }

    LaunchedEffect(exoPlayer) {
        if (playerVideoWidth <= 0 || playerVideoHeight <= 0) {
            val videoSize = exoPlayer.videoSize
            if (videoSize.width > 0) playerVideoWidth = videoSize.width
            if (videoSize.height > 0) playerVideoHeight = videoSize.height
        }
    }

    PauseOnBackground(exoPlayer)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerVideoWidth = videoSize.width
                playerVideoHeight = videoSize.height
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    pendingSwitchBack = null
                    pendingSwitchTarget = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
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
        isFullscreen = fullscreen
        val act = activity ?: return
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
            delay(15000)
            saveCurrentToHistory()
        }
    }

    fun buildBaseUrl(url: String): String = extractHost(url)

    suspend fun fetchEpisodeM3u8(episodeLink: String): String = withContext(Dispatchers.IO) {
        if (episodeLink.isBlank()) return@withContext ""
        if (isBlockedVideoUrl(episodeLink)) return@withContext ""
        // API 源的选集链接本身就是直链（m3u8/mp4），直接返回播放，不再请求一次
        if (isDirectVideoUrl(episodeLink)) return@withContext episodeLink
        // 间接链接（如 share 页面）二次请求源码，正则匹配 "" 内包含 .m3u8 的路径
        val resolved = resolveIndirectVideoUrl(episodeLink, buildBaseUrl(episodeLink))
        if (resolved.isNotBlank()) return@withContext resolved
        // 二次解析失败则返回空，触发重试/错误提示（点击重试会重复此流程）
        ""
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
        val prevPosition = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
        fetchJob?.cancel()
        saveCurrentToHistory()
        selectedRouteIndex = routeIdx
        selectedEpisodeIndex = epIdx
        val ep = route.episodes[epIdx]
        currentEpisodeUrl = ep.link
        isSwitchingEpisode = true
        isPlayLoading = true
        playErrorMsg = null
        currentM3u8Url = ""
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        pendingSwitchTarget = routeIdx to epIdx
        pendingSwitchBack = {
            selectedRouteIndex = prevRouteIdx
            selectedEpisodeIndex = prevEpIdx
            currentEpisodeUrl = prevEpisodeUrl
            currentM3u8Url = prevM3u8Url
            isSwitchingEpisode = false
            isPlayLoading = false
            if (prevM3u8Url.isNotEmpty()) {
                try {
                    exoPlayer.apply {
                        stop()
                        setMediaItem(
                            buildPlaybackMediaItem(
                                uri = prevM3u8Url,
                                title = pageData?.title,
                                subtitle = pageData?.routes?.getOrNull(prevRouteIdx)
                                    ?.episodes?.getOrNull(prevEpIdx)?.name,
                                artworkUrl = pageData?.coverUrl,
                            ),
                        )
                        prepare()
                        playWhenReady = true
                    }
                    // 回退到上一集要从切换前的位置续播：这里只 prepare 不 seek 的话，
                    // 播放器会从 0 重来，看起来就是"卡一下然后回退播放"。
                    if (prevPosition > 0L) targetPosition = prevPosition
                } catch (e: Exception) {
                    playErrorMsg = "播放器准备失败：${e.message ?: e.toString()}"
                    Toast.makeText(context, playErrorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        fetchJob = scope.launch {
            try {
                val m3u8Raw = fetchEpisodeM3u8(ep.link)
                val m3u8 = filterAds(m3u8Raw)
                if (m3u8.isNotEmpty()) {
                    playErrorMsg = null
                    currentM3u8Url = m3u8
                    pendingSwitchBack = null
                    pendingSwitchTarget = null
                    exoPlayer.apply {
                        stop()
                        setMediaItem(
                            buildPlaybackMediaItem(
                                uri = m3u8,
                                title = pageData?.title,
                                subtitle = ep.name,
                                artworkUrl = pageData?.coverUrl,
                            ),
                        )
                        prepare()
                        playWhenReady = true
                    }
                } else {
                    playErrorMsg = "无法加载播放地址"
                    val action = pendingSwitchBack
                    pendingSwitchBack = null
                    pendingSwitchTarget = null
                    action?.invoke()
                }
            } catch (e: Exception) {
                playErrorMsg = e.message ?: "无法加载播放地址"
                val action = pendingSwitchBack
                pendingSwitchBack = null
                pendingSwitchTarget = null
                action?.invoke()
            } finally {
                isSwitchingEpisode = false
                isPlayLoading = false
            }
        }
    }

    LaunchedEffect(videoUrl, retryKey) {
        if (pageData != null && currentM3u8Url.isNotEmpty()) return@LaunchedEffect
        isLoading = true
        errorMsg = null
        val result: PageData? = withContext(Dispatchers.IO) {
            HttpUtil.get(
                videoUrl, mapOf(
                    "User-Agent" to AppConfig.UA_CHROME_PC,
                    "Referer" to buildBaseUrl(videoUrl),
                )
            ).fold(
                onSuccess = { body -> parseApiDetail(body) },
                onFailure = { null },
            )
        }
        if (result != null && result.routes.isNotEmpty() && result.routes[0].episodes.isNotEmpty()) {
            pageData = result

            val matchUrl = state.initialEpisodeUrl ?: videoUrl
            var foundRouteIdx = 0
            var foundEpIdx = 0
            // API 源：选集即完整直链，按完整链接精确匹配（历史恢复时用）；否则默认首集
            outer@ for ((rIdx, route) in result.routes.withIndex()) {
                for ((eIdx, ep) in route.episodes.withIndex()) {
                    if (ep.link == matchUrl) {
                        foundRouteIdx = rIdx
                        foundEpIdx = eIdx
                        break@outer
                    }
                }
            }

            selectedRouteIndex = foundRouteIdx
            selectedEpisodeIndex = foundEpIdx
            val ep = result.routes[foundRouteIdx].episodes[foundEpIdx]
            currentEpisodeUrl = ep.link
            isPlayLoading = true
            playErrorMsg = null
            currentM3u8Url = ""

            scope.launch {
                try {
                    val m3u8Raw = fetchEpisodeM3u8(ep.link)
                    val m3u8 = filterAds(m3u8Raw)
                    if (m3u8.isNotEmpty()) {
                        playErrorMsg = null
                        currentM3u8Url = m3u8
                        try {
                            exoPlayer.apply {
                                setMediaItem(
                                    buildPlaybackMediaItem(
                                        uri = m3u8,
                                        title = result.title,
                                        subtitle = ep.name,
                                        artworkUrl = result.coverUrl,
                                    ),
                                )
                                prepare()
                                playWhenReady = true
                            }
                        } catch (e: Exception) {
                            playErrorMsg = "播放器准备失败：${e.message ?: e.toString()}"
                            Toast.makeText(context, playErrorMsg, Toast.LENGTH_SHORT).show()
                        }
                        if (state.initialPosition > 0L) {
                            targetPosition = state.initialPosition
                        }
                    } else {
                        playErrorMsg = "无法加载播放地址"
                        currentM3u8Url = ""
                        Toast.makeText(context, "无法加载播放地址", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    playErrorMsg = e.message ?: "无法加载播放地址"
                    currentM3u8Url = ""
                    Toast.makeText(context, playErrorMsg, Toast.LENGTH_SHORT).show()
                } finally {
                    isPlayLoading = false
                }
            }
        } else {
            errorMsg = "无法加载视频信息"
        }
        isLoading = false
    }

    LaunchedEffect(playRetryKey) {
        if (playRetryKey == 0) return@LaunchedEffect
        val pd = pageData ?: return@LaunchedEffect
        if (isPlayLoading) return@LaunchedEffect
        val route = pd.routes.getOrNull(selectedRouteIndex) ?: return@LaunchedEffect
        val ep = route.episodes.getOrNull(selectedEpisodeIndex) ?: return@LaunchedEffect
        isPlayLoading = true
        playErrorMsg = null
        currentM3u8Url = ""
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        try {
            val m3u8Raw = fetchEpisodeM3u8(ep.link)
            val m3u8 = filterAds(m3u8Raw)
            if (m3u8.isNotEmpty()) {
                playErrorMsg = null
                currentM3u8Url = m3u8
                // 广告过滤开关切换触发的重载：在新流上恢复切换时刻的播放位置
                if (pendingResumePosition > 0L) {
                    targetPosition = pendingResumePosition
                    pendingResumePosition = 0L
                }
                try {
                    exoPlayer.apply {
                        setMediaItem(
                            buildPlaybackMediaItem(
                                uri = m3u8,
                                title = pd.title,
                                subtitle = ep.name,
                                artworkUrl = pd.coverUrl,
                            ),
                        )
                        prepare()
                        playWhenReady = true
                    }
                } catch (e: Exception) {
                    playErrorMsg = "播放器准备失败：${e.message ?: e.toString()}"
                    Toast.makeText(context, playErrorMsg, Toast.LENGTH_SHORT).show()
                }
            } else {
                playErrorMsg = "无法加载播放地址"
                Toast.makeText(context, "无法加载播放地址", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            playErrorMsg = e.message ?: "无法加载播放地址"
            Toast.makeText(context, playErrorMsg, Toast.LENGTH_SHORT).show()
        } finally {
            isPlayLoading = false
            pendingResumePosition = 0L
        }
    }

    // 切换广告过滤开关后重载当前集（重新应用/取消过滤），并恢复切换时刻的播放进度，
    // 否则开关只在下次换集/重试时才生效，用户会认为过滤无效
    var lastAdsFilter by remember { mutableStateOf(GlobalPlayerConfig.adsFilter) }
    LaunchedEffect(GlobalPlayerConfig.adsFilter) {
        val current = GlobalPlayerConfig.adsFilter
        if (current == lastAdsFilter) return@LaunchedEffect
        lastAdsFilter = current
        if (pageData == null || isPlayLoading || isSwitchingEpisode || currentM3u8Url.isEmpty()) {
            return@LaunchedEffect
        }
        pendingResumePosition = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
        playRetryKey++
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
        MiuixScaffold(
            modifier = modifier.fillMaxSize(),
        ) { paddingValues ->
            if (pageData == null) {
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
                    if (errorMsg != null && !isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixText(
                                text = errorMsg ?: "",
                                color = Color.Red,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            } else {
                val data = pageData!!
                val currentRoute = data.routes.getOrNull(selectedRouteIndex) ?: return@MiuixScaffold
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
                            sheetHeight = sheetMaxHeight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PlayerPlaceholder(
                            onBack = wrappedOnBack,
                            isLoading = isPlayLoading || isSwitchingEpisode,
                            onRetry = if (!isPlayLoading && !isSwitchingEpisode) ({
                                // retry play url for current episode
                                playRetryKey++
                            }) else null,
                            errorText = playErrorMsg,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showIntroSheet = true },
                            showIndication = true,
                            insideMargin = PaddingValues(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {
                                            onViewImage(
                                                data.coverUrl,
                                                buildBaseUrl(videoUrl),
                                                data.title
                                            )
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
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .background(
                                                color = MiuixTheme.colorScheme.primaryContainer
                                                    .copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                    ) {
                                        MiuixText(
                                            text = AppConfig.sourceName(
                                                AppConfig.sourceKey(
                                                    extractHost(
                                                        videoUrl
                                                    )
                                                )
                                            ),
                                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                                            style = MiuixTheme.textStyles.footnote2,
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
                                    MiuixText(
                                        text = data.title,
                                        style = MiuixTheme.textStyles.title4,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    MiuixText(
                                        text = "类型：${data.type}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    MiuixText(
                                        text = "导演：${data.director}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    MiuixText(
                                        text = "主演：${data.actor}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    MiuixText(
                                        text = "简介：${data.description}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                onClick = { },
                                showIndication = true,
                                insideMargin = PaddingValues(vertical = 8.dp),
                            ) {
                                ActionIconItem(
                                    icon = MiuixIcons.Glass.StarOutline,
                                    label = "收藏",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                onClick = { },
                                showIndication = true,
                                insideMargin = PaddingValues(vertical = 8.dp),
                            ) {
                                ActionIconItem(
                                    icon = MiuixIcons.Glass.Download,
                                    label = "缓存",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                onClick = { showSpeedSheet = true },
                                showIndication = true,
                                insideMargin = PaddingValues(vertical = 8.dp),
                            ) {
                                ActionIconItem(
                                    icon = MiuixIcons.Glass.PlaybackSpeed,
                                    label = "倍速",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                onClick = { showScaleSheet = true },
                                showIndication = true,
                                insideMargin = PaddingValues(vertical = 8.dp),
                            ) {
                                ActionIconItem(
                                    icon = MiuixIcons.Glass.ScreenSize,
                                    label = "画幅",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                onClick = { showMoreSheet = true },
                                showIndication = true,
                                insideMargin = PaddingValues(vertical = 8.dp),
                            ) {
                                ActionIconItem(
                                    icon = MiuixIcons.Glass.MoreCircle,
                                    label = "更多",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(
                                "线路",
                                style = MiuixTheme.textStyles.subtitle,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MiuixText(
                                "正在使用：${currentRoute.name}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                                MiuixText(
                                    "${totalRoutes}个线路",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                                MiuixIcon(
                                    painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
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
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        val currentEpisode = currentRoute.episodes.getOrNull(selectedEpisodeIndex)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(
                                "选集",
                                style = MiuixTheme.textStyles.subtitle,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MiuixText(
                                "正在播放：${currentEpisode?.name ?: ""}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                                MiuixText(
                                    "${totalEpisodes}个视频",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                                MiuixIcon(
                                    painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
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
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showMoreSheet) {
        AppBottomSheet(
            show = showMoreSheet,
            onDismissRequest = { showMoreSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                MoreSettingsContent(
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    backgroundPlayback = GlobalPlayerConfig.backgroundPlayback,
                    adsFilter = GlobalPlayerConfig.adsFilter,
                    playbackOrder = GlobalPlayerConfig.playbackOrder,
                    longPressSpeed = GlobalPlayerConfig.longPressSpeed,
                    onFlipHorizontalChange = { flipHorizontal = it },
                    onFlipVerticalChange = { flipVertical = it },
                    onBackgroundPlaybackChange = {
                        GlobalPlayerConfig.setBackgroundPlayback(context, it)
                    },
                    onAdsFilterChange = { GlobalPlayerConfig.setAdsFilter(context, it) },
                    onPlaybackOrderChange = { GlobalPlayerConfig.setPlaybackOrder(context, it) },
                    onLongPressSpeedChange = { GlobalPlayerConfig.setLongPressSpeed(context, it) },
                    onDismiss = { showMoreSheet = false },
                    useFixedDarkColors = false,
                    showTitle = false,
                    scrollable = false,
                )
            }
        }
    }

    if (showIntroSheet && pageData != null) {
        IntroBottomSheet(
            data = pageData!!,
            height = sheetMaxHeight,
            onDismiss = { showIntroSheet = false },
        )
    }

    if (showRouteSheet && pageData != null) {
        RouteBottomSheet(
            routes = pageData!!.routes,
            height = sheetMaxHeight,
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
            height = sheetMaxHeight,
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
            height = sheetMaxHeight,
        )
    }

    if (showScaleSheet) {
        ScaleBottomSheet(
            selectedIndex = currentScaleModeIndex,
            onSelect = { index ->
                currentScaleModeIndex = index
            },
            onDismiss = { showScaleSheet = false },
            height = sheetMaxHeight,
        )
    }
}

@Composable
private fun PlayerPlaceholder(
    onBack: () -> Unit,
    isLoading: Boolean = true,
    onRetry: (() -> Unit)? = null,
    errorText: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color.Black),
    ) {
        MiuixIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 2.dp),
        ) {
            MiuixIcon(
                imageVector = MiuixIcons.Glass.ChevronBackward,
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
                isLoading -> CircularProgressIndicator(size = 56.dp)
                onRetry != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!errorText.isNullOrBlank()) {
                        MiuixText(
                            text = errorText,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.primaryContainer)
                            .clickable { onRetry() }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        MiuixText(
                            text = "点击重试",
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                else -> MiuixIcon(
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
            PlayerFilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = label,
                modifier = Modifier.onGloballyPositioned { coords ->
                    itemXs[index] = coords.positionInParent().x.roundToInt()
                    itemWidths[index] = coords.size.width
                },
            )
        }
    }
}

@Composable
private fun ActionIconItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = label,
            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        MiuixText(
            label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}


@Composable
private fun IntroBottomSheet(
    data: PageData,
    height: Dp,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 32.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                MiuixText(
                    text = data.title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                )
                if (data.type.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MiuixText(
                        text = data.type,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                MiuixText(
                    text = "简介",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                MiuixText(
                    text = data.description,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )

                if (data.director.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    MiuixText(
                        text = "导演",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiuixText(
                        text = data.director,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }

                if (data.actor.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    MiuixText(
                        text = "主演",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiuixText(
                        text = data.actor,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteBottomSheet(
    routes: List<RouteInfo>,
    height: Dp,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedIndex) {
        withFrameNanos { }
        repeat(10) {
            bringIntoViewRequester.bringIntoView()
            withFrameNanos { }
        }
    }
    AppBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .verticalScroll(rememberScrollState()),
        ) {
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
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeBottomSheet(
    episodes: List<EpisodeInfo>,
    height: Dp,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            EpisodePanelContent(
                episodes = episodes,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                useFixedDarkColors = false,
            )
        }
    }
}