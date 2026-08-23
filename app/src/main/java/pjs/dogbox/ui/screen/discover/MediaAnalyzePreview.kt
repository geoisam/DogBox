package pjs.dogbox.ui.screen.discover

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import pjs.dogbox.R
import pjs.dogbox.service.MediaDownloadService
import pjs.dogbox.ui.screen.movie.ALL_SCALE_MODES
import pjs.dogbox.ui.screen.movie.GlobalPlayerConfig
import pjs.dogbox.ui.screen.movie.MoreSettingsContent
import pjs.dogbox.ui.screen.movie.PlayerSeekButton
import pjs.dogbox.ui.screen.movie.ScaleBottomSheet
import pjs.dogbox.ui.screen.movie.SeekBar
import pjs.dogbox.ui.screen.movie.SpeedBottomSheet
import pjs.dogbox.ui.screen.movie.VideoPlayerScreen
import pjs.dogbox.ui.screen.movie.formatTime
import pjs.dogbox.ui.screen.movie.getQualityLabel
import pjs.dogbox.ui.screen.movie.getScaleModes
import pjs.dogbox.ui.screen.movie.refreshEndedFrame
import pjs.dogbox.data.AppData
import pjs.dogbox.ui.components.SimpleDragHandle

private fun setSystemBarsVisible(
    controller: WindowInsetsControllerCompat?,
    visible: Boolean,
) {
    if (controller == null) return
    controller.isAppearanceLightStatusBars = false
    controller.isAppearanceLightNavigationBars = false
    controller.systemBarsBehavior = if (visible)
        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    else
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
    else controller.hide(WindowInsetsCompat.Type.systemBars())
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAnalyzePreview(
    videoUrls: List<String>,
    initialIndex: Int,
    title: String,
    referer: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    GlobalPlayerConfig.init(context)

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (videoUrls.size - 1).coerceAtLeast(0)),
        pageCount = { videoUrls.size },
    )
    val currentVideoUrl = videoUrls[pagerState.currentPage]

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

    var isFullscreen by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var pendingShow by remember { mutableStateOf(false) }
    var bgColorIndex by remember { mutableIntStateOf(0) }
    val bgColors = listOf(Color.Black, Color.White, Color.Gray)

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    var playerCurrentPosition by remember { mutableStateOf(0L) }
    var playerDuration by remember { mutableStateOf(0L) }
    var playerBufferedPercentage by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(1f) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    val longPressSpeed = GlobalPlayerConfig.longPressSpeed
    var currentScaleModeIndex by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    val backgroundPlayback = GlobalPlayerConfig.backgroundPlayback
    val playbackOrder = GlobalPlayerConfig.playbackOrder

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showScaleSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val currentScaleMode =
        getScaleModes(false)[currentScaleModeIndex.coerceIn(0, ALL_SCALE_MODES.lastIndex)]

    val insetsController = remember {
        activity?.window?.let {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val ctrl = ViewCompat.getWindowInsetsController(view)
        val savedLightStatus = ctrl?.isAppearanceLightStatusBars
        val savedLightNav = ctrl?.isAppearanceLightNavigationBars
        ctrl?.isAppearanceLightStatusBars = false
        ctrl?.isAppearanceLightNavigationBars = false
        onDispose {
            setSystemBarsVisible(ctrl, true)
            if (savedLightStatus != null) ctrl.isAppearanceLightStatusBars = savedLightStatus
            if (savedLightNav != null) ctrl.isAppearanceLightNavigationBars = savedLightNav
        }
    }

    LaunchedEffect(showSpeedSheet, showScaleSheet, showMoreSheet) {
        if (showSpeedSheet || showScaleSheet || showMoreSheet) {
            setSystemBarsVisible(insetsController, true)
            showBars = true
        }
    }

    LaunchedEffect(pendingShow) {
        if (pendingShow) {
            setSystemBarsVisible(insetsController, true)
            showBars = true
            pendingShow = false
        }
    }

    val exoPlayer = remember {
        val headers = HashMap<String, String>().apply {
            if (referer.isNotBlank()) put("Referer", referer)
            put("User-Agent", AppData.UA_WECHAT_PC)
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(AppData.UA_WECHAT_PC)
            .setDefaultRequestProperties(headers)
        val sourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(sourceFactory)
            .build()
    }

    LaunchedEffect(currentVideoUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(currentVideoUrl)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        isPlaying = false
        isBuffering = true
        playerCurrentPosition = 0L
        playerDuration = 0L
        videoWidth = 0
        videoHeight = 0
    }

    val playerListener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
    }

    DisposableEffect(exoPlayer) {
        isPlaying = exoPlayer.isPlaying
        exoPlayer.addListener(playerListener)
        onDispose {
            exoPlayer.removeListener(playerListener)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            playerCurrentPosition = exoPlayer.currentPosition
            playerDuration = exoPlayer.duration
            playerBufferedPercentage = exoPlayer.bufferedPercentage
            delay(200)
        }
    }

    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            delay(200)
            when (exoPlayer.playbackState) {
                Player.STATE_ENDED -> {
                    val pos = exoPlayer.duration
                    if (pos > 0) exoPlayer.seekTo((pos - 1).coerceAtLeast(0L))
                }
                Player.STATE_READY -> if (!exoPlayer.isPlaying) {
                    exoPlayer.seekTo(exoPlayer.currentPosition)
                }
                else -> {}
            }
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        val act = activity ?: return
        val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
        if (fullscreen) {
            act.requestedOrientation = if (videoWidth > videoHeight)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun togglePlayPause() {
        if (playerDuration > 0 && exoPlayer.currentPosition >= playerDuration - 500) {
            exoPlayer.seekTo(0)
        }
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun toggleMute() {
        isMuted = !isMuted
        if (isMuted) {
            previousVolume = exoPlayer.volume
            exoPlayer.volume = 0f
        } else {
            exoPlayer.volume = previousVolume
        }
    }

    fun copyUrl() {
        copyText(context, currentVideoUrl)
    }

    fun openInBrowser() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, currentVideoUrl.toUri()))
        }.onFailure {
            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveVideo() {
        if (!ensureFileAccess(context)) return
        ensureNotificationPermission {
            MediaDownloadService.start(
                context,
                listOf(currentVideoUrl),
                referer,
                title,
                "Videos",
                "mp4",
            )
            Toast.makeText(context, "开始下载，请查看通知栏进度", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        if (isFullscreen) {
            setFullscreen(false)
        } else {
            onBack()
        }
    }

    if (isFullscreen) {
        VideoPlayerScreen(
            exoPlayer = exoPlayer,
            title = title,
            isVod = true,
            isFullscreen = true,
            onFullscreenChange = { setFullscreen(it) },
            onBack = { setFullscreen(false) },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColors[bgColorIndex])
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showBars) {
                            showBars = false
                            setSystemBarsVisible(insetsController, false)
                        } else {
                            pendingShow = true
                        }
                    },
                )
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        val pv = LayoutInflater.from(ctx)
                            .inflate(R.layout.player_view_texture, null) as PlayerView
                        pv.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            player = exoPlayer
                            resizeMode = currentScaleMode.resizeMode
                            refreshEndedFrame(exoPlayer)
                        }
                    },
                    update = { pv ->
                        pv.resizeMode = currentScaleMode.resizeMode
                        pv.scaleX = if (flipHorizontal) -1f else 1f
                        pv.scaleY = if (flipVertical) -1f else 1f
                    },
                    modifier = currentScaleMode.aspectRatio?.let { ratio ->
                        val hByHeight = screenHeightDp.toFloat()
                        val wByHeight = hByHeight * ratio
                        val w: Float
                        val h: Float
                        if (wByHeight <= screenWidthDp.toFloat()) {
                            h = hByHeight
                            w = wByHeight
                        } else {
                            w = screenWidthDp.toFloat()
                            h = w / ratio
                        }
                        Modifier
                            .width(w.dp)
                            .height(h.dp)
                    } ?: Modifier.fillMaxSize(),
                )
            }
        }

        if (!isPlaying && !isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FilledTonalIconButton(
                    onClick = { togglePlayPause() },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(0.3f),
                    ),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play_filled),
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(
                animationSpec = tween(200),
                initialOffsetY = { -it },
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { -it },
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black, Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_outlined),
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { bgColorIndex = (bgColorIndex + 1) % bgColors.size }) {
                    Icon(
                        painter = painterResource(R.drawable.sync_alt_outlined),
                        contentDescription = "切换背景色",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { showMoreSheet = true }) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz_filled),
                        contentDescription = "更多",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(
                animationSpec = tween(200),
                initialOffsetY = { it },
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { it },
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${formatTime(playerCurrentPosition)} / ${formatTime(playerDuration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    PlayerSeekButton(
                        icon = R.drawable.replay_10_outlined,
                        text = "后退10s",
                        onClick = {
                            exoPlayer.seekTo(
                                (exoPlayer.currentPosition - 10_000).coerceAtLeast(0L)
                            )
                        },
                    )
                    PlayerSeekButton(
                        icon = R.drawable.forward_10_outlined,
                        text = "快进10s",
                        onClick = {
                            exoPlayer.seekTo(
                                (exoPlayer.currentPosition + 10_000).coerceAtMost(playerDuration)
                            )
                        },
                    )
                }

                SeekBar(
                    playedFraction = if (playerDuration > 0) (playerCurrentPosition.toFloat() / playerDuration) else 0f,
                    bufferedFraction = playerBufferedPercentage / 100f,
                    onSeek = { fraction ->
                        exoPlayer.seekTo((fraction * playerDuration).toLong())
                    },
                    onSeekFinished = {},
                    modifier = Modifier.padding(horizontal = 12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = { togglePlayPause() }) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.pause_filled else R.drawable.play_filled
                            ),
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = { toggleMute() }) {
                        Icon(
                            painter = painterResource(
                                if (isMuted) R.drawable.volume_off_outlined
                                else R.drawable.volume_up_outlined
                            ),
                            contentDescription = if (isMuted) "恢复音量" else "静音",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${pagerState.currentPage + 1}/${videoUrls.size}",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getQualityLabel(videoWidth, videoHeight),
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(onClick = { setFullscreen(true) }) {
                        Icon(
                            painter = painterResource(R.drawable.fullscreen_filled),
                            contentDescription = "全屏播放",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerActionItem(
                        icon = R.drawable.open_in_browser_outlined,
                        label = "打开",
                        onClick = { openInBrowser() },
                    )
                    PlayerActionItem(
                        icon = R.drawable.content_copy_outlined,
                        label = "复制链接",
                        onClick = { copyUrl() },
                    )
                    PlayerActionItem(
                        icon = R.drawable.download_outlined,
                        label = "保存视频",
                        onClick = { saveVideo() },
                    )
                    PlayerActionItem(
                        icon = R.drawable.speed_outlined,
                        label = "播放倍速",
                        onClick = { showSpeedSheet = true },
                    )
                    PlayerActionItem(
                        icon = R.drawable.aspect_ratio_outlined,
                        label = "视频比例",
                        onClick = { showScaleSheet = true },
                    )
                }
            }
        }
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
            onSelect = {
                currentScaleModeIndex = it
                showScaleSheet = false
            },
            onDismiss = { showScaleSheet = false },
        )
    }

    if (showMoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = sheetState,
            dragHandle = { SimpleDragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                MoreSettingsContent(
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    backgroundPlayback = backgroundPlayback,
                    adFilter = GlobalPlayerConfig.adFilter,
                    playbackOrder = playbackOrder,
                    longPressSpeed = longPressSpeed,
                    onFlipHorizontalChange = { flipHorizontal = it },
                    onFlipVerticalChange = { flipVertical = it },
                    onBackgroundPlaybackChange = { GlobalPlayerConfig.setBackgroundPlayback(context, it) },
                    onAdFilterChange = { GlobalPlayerConfig.setAdFilter(context, it) },
                    onPlaybackOrderChange = { GlobalPlayerConfig.setPlaybackOrder(context, it) },
                    onLongPressSpeedChange = { GlobalPlayerConfig.setLongPressSpeed(context, it) },
                    onDismiss = { showMoreSheet = false },
                    useFixedDarkColors = false,
                )
            }
        }
    }
}

@Composable
private fun PlayerActionItem(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}