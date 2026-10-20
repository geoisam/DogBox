package pjs.dogbox.ui.screen.mine

import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.data.localvideo.LocalVideoStore
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.ui.screen.movie.ALL_SCALE_MODES
import pjs.dogbox.ui.screen.movie.GlobalPlayerConfig
import pjs.dogbox.ui.screen.movie.MoreSettingsContent
import pjs.dogbox.ui.screen.movie.PauseOnBackground
import pjs.dogbox.ui.screen.movie.PlaybackOrder
import pjs.dogbox.ui.screen.movie.PlayerSeekButton
import pjs.dogbox.ui.screen.movie.ScaleBottomSheet
import pjs.dogbox.ui.screen.movie.SeekBar
import pjs.dogbox.ui.screen.movie.SpeedBottomSheet
import pjs.dogbox.ui.screen.movie.VideoPlayerScreen
import pjs.dogbox.ui.screen.movie.formatTime
import pjs.dogbox.ui.screen.movie.getQualityLabel
import pjs.dogbox.ui.screen.movie.getScaleModes
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

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

private fun resolveVideoFile(context: android.content.Context, uriString: String): File? {
    val uri = Uri.parse(uriString)
    return when (uri.scheme) {
        "file" -> uri.path?.let { File(it) }
        "content" -> {
            try {
                context.contentResolver
                    .query(uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(MediaStore.Video.Media.DATA)
                            if (idx >= 0) c.getString(idx)?.let { File(it) } else null
                        } else null
                    }
            } catch (_: Exception) {
                null
            }
        }

        else -> null
    }
}

private fun renameVideoFile(
    context: android.content.Context,
    uriString: String,
    newName: String
): File? {
    return try {
        val uri = Uri.parse(uriString)
        val file = resolveVideoFile(context, uriString) ?: return null
        val parent = file.parentFile ?: return null
        val newFile = File(parent, newName)
        if (newFile.exists()) return null
        if (!file.renameTo(newFile)) return null
        if (uri.scheme == "content") {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, newName)
                    put(MediaStore.Video.Media.DATA, newFile.absolutePath)
                }
                context.contentResolver.update(uri, values, null, null)
            }
        }
        newFile
    } catch (_: Exception) {
        null
    }
}

private fun deleteVideoFile(context: android.content.Context, uriString: String): Boolean {
    return try {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> File(uri.path ?: return false).delete()
            "content" -> context.contentResolver.delete(uri, null, null) > 0
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LocalVideoPreview(
    videoUri: String,
    title: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    var isFullscreen by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var pendingShow by remember { mutableStateOf(false) }
    var bgColorIndex by remember { mutableIntStateOf(0) }
    val bgColors = listOf(Color.Black, Color.White, Color.Gray)

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    val originalExt = title.substringAfterLast('.', "")
    var displayTitle by remember { mutableStateOf(title.substringBeforeLast('.')) }

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

    var showRenameSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showScaleSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val currentScaleMode =
        getScaleModes(false)[currentScaleModeIndex.coerceIn(0, ALL_SCALE_MODES.lastIndex)]

    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale == 1f) {
            offset = Offset.Zero
        } else {
            offset += panChange
        }
        scale = newScale
    }

    val insetsController = remember {
        activity?.window?.let {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        }
    }

    LaunchedEffect(showRenameSheet, showDeleteDialog, showSpeedSheet, showMoreSheet) {
        if (showRenameSheet || showDeleteDialog || showSpeedSheet || showMoreSheet) {
            setSystemBarsVisible(insetsController, true)
            showBars = true
        }
    }

    val currentPlaybackOrder by rememberUpdatedState(playbackOrder)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(playbackOrder) {
        exoPlayer.repeatMode = when (playbackOrder) {
            PlaybackOrder.SINGLE_LOOP -> Player.REPEAT_MODE_ONE
            PlaybackOrder.LIST_LOOP -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    PauseOnBackground(exoPlayer)

    val playerListener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    when (currentPlaybackOrder) {
                        PlaybackOrder.SINGLE_LOOP, PlaybackOrder.LIST_LOOP -> {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        }

                        PlaybackOrder.AUTO_NEXT, PlaybackOrder.STOP_AFTER -> {}
                    }
                }
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

    LaunchedEffect(pendingShow) {
        if (pendingShow) {
            setSystemBarsVisible(insetsController, true)
            showBars = true
            pendingShow = false
        }
    }

    LaunchedEffect(Unit) {
        setSystemBarsVisible(insetsController, true)
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

    fun shareVideo() {
        try {
            val uri = Uri.parse(videoUri)
            val shareUri = if (uri.scheme == "file") {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(uri.path!!),
                )
            } else {
                uri
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享视频"))
        } catch (_: Exception) {
            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmRename(newName: String) {
        val base = newName.trim()
        if (base.isEmpty()) {
            Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        val finalName = if (originalExt.isNotEmpty()) "$base.$originalExt" else base
        scope.launch {
            val newFile = withContext(Dispatchers.IO) {
                renameVideoFile(context, videoUri, finalName)
            }
            Toast.makeText(
                context,
                if (newFile != null) "重命名成功" else "重命名失败",
                Toast.LENGTH_SHORT,
            ).show()
            if (newFile != null) {
                val oldUri = Uri.parse(videoUri)
                val newUri = if (oldUri.scheme == "file")
                    Uri.fromFile(newFile).toString()
                else
                    videoUri
                LocalVideoStore.updateVideo(
                    context,
                    videoUri,
                    newUri,
                    finalName,
                    newFile.absolutePath,
                )
                displayTitle = base
                showRenameSheet = false
            }
        }
    }

    fun confirmDelete() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                deleteVideoFile(context, videoUri)
            }
            Toast.makeText(
                context,
                if (ok) "已删除" else "删除失败",
                Toast.LENGTH_SHORT,
            ).show()
            if (ok) {
                LocalVideoStore.removeVideo(context, videoUri)
                onBack()
            }
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
            title = displayTitle,
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
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformableState)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
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
                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                            val pos = exoPlayer.duration
                            if (pos > 0) exoPlayer.seekTo((pos - 1).coerceAtLeast(0L))
                        }
                    }
                },
                update = { pv ->
                    pv.resizeMode = currentScaleMode.resizeMode
                    pv.scaleX = if (flipHorizontal) -1f else 1f
                    pv.scaleY = if (flipVertical) -1f else 1f
                },
                onRelease = { runCatching { it.player = null } },
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

        if (!isPlaying && !isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.3f))
                        .clickable { togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixIcon(
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
                MiuixIconButton(onClick = onBack) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.arrow_back_outlined),
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                MiuixText(
                    text = displayTitle,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MiuixTheme.textStyles.title4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MiuixIconButton(onClick = { bgColorIndex = (bgColorIndex + 1) % bgColors.size }) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.sync_alt_outlined),
                        contentDescription = "切换背景色",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                MiuixIconButton(onClick = {}) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.cast_outlined),
                        contentDescription = "投屏",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                MiuixIconButton(onClick = { showMoreSheet = true }) {
                    MiuixIcon(
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
                    MiuixText(
                        text = "${formatTime(playerCurrentPosition)} / ${formatTime(playerDuration)}",
                        color = Color.White,
                        style = MiuixTheme.textStyles.body2,
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
                    MiuixIconButton(onClick = { togglePlayPause() }) {
                        MiuixIcon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.pause_filled else R.drawable.play_filled
                            ),
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    MiuixIconButton(onClick = { toggleMute() }) {
                        MiuixIcon(
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
                    MiuixText(
                        text = getQualityLabel(videoWidth, videoHeight),
                        color = Color.White.copy(alpha = 0.5f),
                        style = MiuixTheme.textStyles.footnote1,
                    )
                    MiuixIconButton(onClick = { setFullscreen(true) }) {
                        MiuixIcon(
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
                        icon = R.drawable.ios_share_outlined,
                        label = "发送",
                        onClick = { shareVideo() },
                    )
                    PlayerActionItem(
                        icon = R.drawable.edit_square_outlined,
                        label = "重命名",
                        onClick = { showRenameSheet = true },
                    )
                    PlayerActionItem(
                        icon = R.drawable.delete_outlined,
                        label = "删除",
                        onClick = { showDeleteDialog = true },
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

    if (showRenameSheet) {
        var renameValue by remember {
            mutableStateOf(TextFieldValue(displayTitle, TextRange(0, displayTitle.length)))
        }
        val sheetView = LocalView.current
        val focusRequester = remember { FocusRequester() }
        androidx.compose.runtime.SideEffect {
            ViewCompat.getWindowInsetsController(sheetView)
                ?.isAppearanceLightStatusBars = false
            ViewCompat.getWindowInsetsController(sheetView)
                ?.isAppearanceLightNavigationBars = false
        }
        LaunchedEffect(Unit) {
            delay(200)
            focusRequester.requestFocus()
        }
        AppBottomSheet(
            show = true,
            title = "重命名",
            onDismissRequest = { showRenameSheet = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                MiuixTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showRenameSheet = false },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { confirmRename(renameValue.text) },
                        modifier = Modifier.weight(1f),
                    ) {
                        MiuixText("确认")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    OverlayDialog(
        show = showDeleteDialog,
        title = "删除",
        summary = "确认删除这1项内容？删除后将无法查看。",
        onDismissRequest = { showDeleteDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = "取消",
                onClick = { showDeleteDialog = false },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { showDeleteDialog = false; confirmDelete() },
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                ),
            ) {
                MiuixText(
                    text = "确认删除",
                    color = MiuixTheme.colorScheme.onError,
                )
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
        AppBottomSheet(
            show = true,
            onDismissRequest = { showMoreSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                MoreSettingsContent(
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    backgroundPlayback = backgroundPlayback,
                    adsFilter = GlobalPlayerConfig.adsFilter,
                    playbackOrder = playbackOrder,
                    longPressSpeed = longPressSpeed,
                    onFlipHorizontalChange = { flipHorizontal = it },
                    onFlipVerticalChange = { flipVertical = it },
                    onBackgroundPlaybackChange = {
                        GlobalPlayerConfig.setBackgroundPlayback(
                            context,
                            it
                        )
                    },
                    onAdsFilterChange = { GlobalPlayerConfig.setAdsFilter(context, it) },
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiuixIcon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        MiuixText(
            text = label,
            color = Color.White,
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}
