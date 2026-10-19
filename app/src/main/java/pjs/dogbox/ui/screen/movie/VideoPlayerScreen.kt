@file:OptIn(ExperimentalLayoutApi::class)

package pjs.dogbox.ui.screen.movie

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pjs.dogbox.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import pjs.dogbox.ui.components.AppBottomSheet


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    exoPlayer: ExoPlayer,
    title: String?,
    isVod: Boolean = true,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    scaleModeIndex: Int = 0,
    onScaleModeIndexChange: ((Int) -> Unit)? = null,
    sheetHeight: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var playerIsPlaying by remember { mutableStateOf(false) }
    var playerIsBuffering by remember { mutableStateOf(true) }
    var playerCurrentPosition by remember { mutableStateOf(0L) }
    var playerDuration by remember { mutableStateOf(0L) }
    var playerBufferedPercentage by remember { mutableIntStateOf(0) }
    var playerVideoWidth by remember { mutableIntStateOf(0) }
    var playerVideoHeight by remember { mutableIntStateOf(0) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    val longPressSpeed = GlobalPlayerConfig.longPressSpeed
    var isMuted by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    var currentScaleModeIndex by remember(scaleModeIndex) {
        mutableIntStateOf(
            mapAllToPlayerScale(
                scaleModeIndex,
                isLandscape
            )
        )
    }
    val currentScaleMode = getScaleModes(isLandscape).getOrElse(currentScaleModeIndex) { ALL_SCALE_MODES.first() }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var locked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var bgColorIndex by remember { mutableIntStateOf(0) }
    val bgColors = listOf(Color.Black, Color.White, Color.Gray)

    var currentTimeText by remember { mutableStateOf("未知") }
    var batteryPercentText by remember { mutableStateOf("未知") }

    LaunchedEffect(Unit) {
        currentTimeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        while (true) {
            delay(30000)
            currentTimeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    LaunchedEffect(Unit) {
        val intent = runCatching {
            ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.getOrNull()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        batteryPercentText = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else ""
    }

    val window = remember(context) { (context as? android.app.Activity)?.window }
    val audioManager =
        remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    @Suppress("DEPRECATION")
    var brightness by remember {
        mutableFloatStateOf(
            (window?.attributes?.screenBrightness ?: -1f)
        )
    }
    var savedSystemBrightness by remember { mutableFloatStateOf(-1f) }
    var isRewinding by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    var isAdjustingBrightness by remember { mutableStateOf(false) }
    var isAdjustingVolume by remember { mutableStateOf(false) }
    var brightnessPercent by remember { mutableIntStateOf(50) }
    var volumePercent by remember { mutableIntStateOf(50) }
    var previousVolume by remember { mutableFloatStateOf(1f) }
    var previousVolumePercent by remember { mutableIntStateOf(50) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }
    var volumeHideJob by remember { mutableStateOf<Job?>(null) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showScaleSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var userToggledControls by remember { mutableStateOf(false) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    val backgroundPlayback = GlobalPlayerConfig.backgroundPlayback
    val playbackOrder = GlobalPlayerConfig.playbackOrder
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewPosition by remember { mutableStateOf(0L) }
    var seekHideJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun toggleMute() {
        isMuted = !isMuted
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (isMuted) {
            previousVolume = exoPlayer.volume
            previousVolumePercent = volumePercent
            exoPlayer.volume = 0f
            volumePercent = 0
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } else {
            exoPlayer.volume = previousVolume
            volumePercent = previousVolumePercent
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (previousVolumePercent * maxVol / 100).coerceIn(0, maxVol),
                0
            )
        }
    }

    @Suppress("DEPRECATION")
    LaunchedEffect(brightness) {
        val lp = window?.attributes ?: return@LaunchedEffect
        lp.screenBrightness = brightness
        window.attributes = lp
    }

    @Suppress("DEPRECATION")
    LaunchedEffect(isFullscreen) {
        val lp = window?.attributes ?: return@LaunchedEffect
        if (isFullscreen) {
            savedSystemBrightness = lp.screenBrightness
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            lp.screenBrightness = savedSystemBrightness
            window.attributes = lp
            brightness = savedSystemBrightness
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    LaunchedEffect(isRewinding) {
        if (isRewinding) {
            exoPlayer.pause()
            while (true) {
                val newPos = (exoPlayer.currentPosition - 100).coerceAtLeast(0L)
                exoPlayer.seekTo(newPos)
                delay(100)
            }
        }
    }

    LaunchedEffect(isFastForwarding) {
        if (isFastForwarding) {
            val origSpeed = currentSpeed
            exoPlayer.playbackParameters = PlaybackParameters(3.0f)
            if (!exoPlayer.isPlaying) exoPlayer.play()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                exoPlayer.playbackParameters = PlaybackParameters(origSpeed)
            }
        }
    }

    val currentPlaybackOrder by rememberUpdatedState(playbackOrder)

    val playerListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playerIsBuffering = playbackState == Player.STATE_BUFFERING
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

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerIsPlaying = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerVideoWidth = videoSize.width
                playerVideoHeight = videoSize.height
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                currentSpeed = playbackParameters.speed
            }

            override fun onPlayerError(error: PlaybackException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "播放出错：${error.localizedMessage}", Toast.LENGTH_SHORT)
                        .show()
                }
                if (isFullscreen) {
                    onFullscreenChange(false)
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        playerIsBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
        playerIsPlaying = exoPlayer.isPlaying
        exoPlayer.addListener(playerListener)
        onDispose {
            exoPlayer.removeListener(playerListener)
        }
    }

    LaunchedEffect(playbackOrder) {
        exoPlayer.repeatMode = when (playbackOrder) {
            PlaybackOrder.SINGLE_LOOP -> Player.REPEAT_MODE_ONE
            PlaybackOrder.LIST_LOOP -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                playerCurrentPosition = exoPlayer.currentPosition
                playerDuration = exoPlayer.duration
                playerBufferedPercentage = exoPlayer.bufferedPercentage
            } catch (_: Exception) {
                // exoPlayer may have been released
            }
            delay(200)
        }
    }

    DisposableEffect(window) {
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            onDispose { }
        }
    }

    PauseOnBackground(exoPlayer)

    fun onInteraction() {
        if (showControls) {
            showControls = false
            userToggledControls = true
        } else {
            showControls = true
            userToggledControls = false
        }
    }

    fun hideControls() {
        showControls = false
        userToggledControls = true
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }
    val viewConfig = LocalViewConfiguration.current

    LaunchedEffect(showControls, playerIsPlaying, showSpeedSheet, showScaleSheet, showMoreSheet) {
        if (showControls && playerIsPlaying) {
            delay(5000)
            showControls = false
        }
    }

    LaunchedEffect(playerIsPlaying) {
        if (!playerIsPlaying && !userToggledControls) {
            showControls = true
        }
    }

    if (isFullscreen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgColors[bgColorIndex]),
        ) {
            PlayerCore(
                exoPlayer = exoPlayer,
                currentScaleMode = currentScaleMode,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical,
                playerIsBuffering = playerIsBuffering,
                isRewinding = isRewinding,
                isFastForwarding = isFastForwarding,
                isAdjustingBrightness = isAdjustingBrightness,
                isAdjustingVolume = isAdjustingVolume,
                brightnessPercent = brightnessPercent,
                volumePercent = volumePercent,
                isSeeking = isSeeking,
                seekPreviewPosition = seekPreviewPosition,
                playerDuration = playerDuration,
                isLandscape = isLandscape,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                onInteraction = { onInteraction() },
                onPlayerViewRef = { playerViewRef = it },
                window = window,
                audioManager = audioManager,
                brightness = brightness,
                onBrightnessChange = { brightness = it; brightnessPercent = (it * 100).toInt() },
                isRewindingChange = { isRewinding = it },
                isFastForwardingChange = { isFastForwarding = it },
                brightnessHideJob = brightnessHideJob,
                onBrightnessHideJobChange = { brightnessHideJob = it },
                volumeHideJob = volumeHideJob,
                onVolumeHideJobChange = { volumeHideJob = it },
                seekHideJob = seekHideJob,
                onSeekHideJobChange = { seekHideJob = it },
                isSeekingChange = { isSeeking = it },
                seekPreviewPositionChange = { seekPreviewPosition = it },
                isAdjustingBrightnessChange = { isAdjustingBrightness = it },
                isAdjustingVolumeChange = { isAdjustingVolume = it },
                onVolumePercentChange = { volumePercent = it },
                scope = scope,
                resetControlsTimer = { hideControls() },
                showControls = showControls,
                playerIsPlaying = playerIsPlaying,
                backgroundColor = bgColors[bgColorIndex],
            )

            if (showControls) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!locked) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                    )
                                )
                                .padding(horizontal = if (isLandscape) 24.dp else 4.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (isLandscape) 12.dp else 32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MiuixText(
                                    text = "时间 $currentTimeText",
                                    color = Color.White,
                                    style = MiuixTheme.textStyles.footnote2,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                MiuixText(
                                    text = "$batteryPercentText 电量",
                                    color = Color.White,
                                    style = MiuixTheme.textStyles.footnote2,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                MiuixIconButton(onClick = {
                                    onFullscreenChange(false)
                                }) {
                                    MiuixIcon(
                                        painter = painterResource(R.drawable.arrow_back_outlined),
                                        contentDescription = "退出全屏",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    MiuixText(
                                        text = title ?: "未知影片",
                                        color = Color.White,
                                        style = MiuixTheme.textStyles.subtitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                MiuixIconButton(onClick = {
                                    bgColorIndex = (bgColorIndex + 1) % bgColors.size
                                }) {
                                    MiuixIcon(
                                        painter = painterResource(R.drawable.sync_alt_outlined),
                                        contentDescription = "切换背景色",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                MiuixIconButton(onClick = {
                                    hideControls()
                                }) {
                                    MiuixIcon(
                                        painter = painterResource(R.drawable.cast_outlined),
                                        contentDescription = "投屏",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                MiuixIconButton(onClick = {
                                    hideControls()
                                    showMoreSheet = true
                                }) {
                                    MiuixIcon(
                                        painter = painterResource(R.drawable.more_horiz_filled),
                                        contentDescription = "更多",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    val downTime = System.currentTimeMillis()
                                    val isDoubleTap =
                                        (downTime - lastTapTime) < viewConfig.doubleTapTimeoutMillis
                                    if (isDoubleTap) {
                                        lastTapTime = 0L
                                        singleTapJob?.cancel()
                                        singleTapJob = null
                                        if (playerDuration > 0 && exoPlayer.currentPosition >= playerDuration - 500) {
                                            exoPlayer.seekTo(0)
                                        }
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Main)
                                            ev.changes.forEach { it.consume() }
                                            if (ev.changes.all { !it.pressed }) break
                                        }
                                        return@awaitEachGesture
                                    }
                                    lastTapTime = downTime
                                    var pastTouchSlop = false
                                    var longPressFired = false
                                    val longPressIsRight = down.position.x > size.width / 2
                                    fun fireLongPress() {
                                        if (longPressFired || pastTouchSlop) return
                                        longPressFired = true
                                        singleTapJob?.cancel()
                                        singleTapJob = null
                                        if (longPressIsRight) isFastForwarding = true
                                        else isRewinding = true
                                    }
                                    var dragAxis =
                                        0 // 0=none, 1=horizontal(seek), 2=vertical(volume/brightness)
                                    var lastPos = down.position
                                    while (true) {
                                        if (!longPressFired && !pastTouchSlop) {
                                            val elapsed =
                                                System.currentTimeMillis() - downTime
                                            if (elapsed >= viewConfig.longPressTimeoutMillis) {
                                                fireLongPress()
                                            }
                                        }
                                        val remaining =
                                            if (!longPressFired && !pastTouchSlop) {
                                                (viewConfig.longPressTimeoutMillis - (System.currentTimeMillis() - downTime)).coerceAtLeast(
                                                    1L
                                                )
                                            } else 0L
                                        val event = if (remaining > 0) {
                                            withTimeoutOrNull(remaining) {
                                                awaitPointerEvent(PointerEventPass.Main)
                                            }
                                        } else {
                                            awaitPointerEvent(PointerEventPass.Main)
                                        }
                                        if (event == null) {
                                            fireLongPress()
                                            continue
                                        }
                                        val change = event.changes.first()
                                        if (!change.pressed) {
                                            if (longPressFired) {
                                                isRewinding = false
                                                isFastForwarding = false
                                            } else if (!pastTouchSlop) {
                                                singleTapJob?.cancel()
                                                singleTapJob = scope.launch {
                                                    delay(viewConfig.doubleTapTimeoutMillis)
                                                    onInteraction()
                                                }
                                            }
                                            break
                                        }
                                        if (!pastTouchSlop && !longPressFired) {
                                            val dx = change.position.x - down.position.x
                                            val dy = change.position.y - down.position.y
                                            if (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop) {
                                                pastTouchSlop = true
                                                dragAxis = if (abs(dx) > abs(dy)) 1 else 2
                                                if (dragAxis == 2) {
                                                    volumeHideJob?.cancel()
                                                    brightnessHideJob?.cancel()
                                                    isAdjustingVolume = false
                                                    isAdjustingBrightness = false
                                                } else {
                                                    seekHideJob?.cancel()
                                                    seekPreviewPosition = exoPlayer.currentPosition
                                                    isSeeking = true
                                                }
                                            }
                                        }
                                        if (pastTouchSlop) {
                                            change.consume()
                                            val delta = change.position - lastPos
                                            if (dragAxis == 2) {
                                                val centerX = size.width / 2
                                                val x = change.position.x
                                                if (x > centerX) {
                                                    val maxVol =
                                                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                    val curVol =
                                                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                    val newVol =
                                                        (curVol - (delta.y / 300f * maxVol).toInt()).coerceIn(
                                                            0,
                                                            maxVol
                                                        )
                                                    audioManager.setStreamVolume(
                                                        AudioManager.STREAM_MUSIC,
                                                        newVol,
                                                        0
                                                    )
                                                    volumePercent = (newVol * 100 / maxVol)
                                                    if (newVol == 0) {
                                                        isMuted = true
                                                        exoPlayer.volume = 0f
                                                    } else {
                                                        isMuted = false
                                                        exoPlayer.volume = 1f
                                                        previousVolumePercent = volumePercent
                                                    }
                                                    if (!isAdjustingVolume) {
                                                        isAdjustingVolume = true
                                                    }
                                                    if (isAdjustingBrightness) {
                                                        brightnessHideJob?.cancel()
                                                        isAdjustingBrightness = false
                                                    }
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    val lp = window?.attributes ?: continue
                                                    val b =
                                                        (lp.screenBrightness - delta.y / 300f).coerceIn(
                                                            0f,
                                                            1f
                                                        )
                                                    lp.screenBrightness = b
                                                    window.attributes = lp
                                                    brightness = b
                                                    brightnessPercent = (b * 100).toInt()
                                                    if (!isAdjustingBrightness) {
                                                        isAdjustingBrightness = true
                                                    }
                                                    if (isAdjustingVolume) {
                                                        volumeHideJob?.cancel()
                                                        isAdjustingVolume = false
                                                    }
                                                }
                                            } else if (dragAxis == 1 && playerDuration > 0) {
                                                val seekDelta =
                                                    (delta.x / size.width * playerDuration).toLong()
                                                val newPos =
                                                    (exoPlayer.currentPosition + seekDelta).coerceIn(
                                                        0,
                                                        playerDuration
                                                    )
                                                exoPlayer.seekTo(newPos)
                                                seekPreviewPosition = newPos
                                            }
                                        }
                                        lastPos = change.position
                                    }
                                    if (pastTouchSlop && !longPressFired) {
                                        if (dragAxis == 2) {
                                            if (isAdjustingVolume) {
                                                volumeHideJob = scope.launch {
                                                    delay(1500)
                                                    isAdjustingVolume = false
                                                }
                                            }
                                            if (isAdjustingBrightness) {
                                                brightnessHideJob = scope.launch {
                                                    delay(1500)
                                                    isAdjustingBrightness = false
                                                }
                                            }
                                        } else if (dragAxis == 1) {
                                            hideControls()
                                            seekHideJob = scope.launch {
                                                delay(1500)
                                                isSeeking = false
                                            }
                                        }
                                    }
                                }
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(horizontal = if (isLandscape) 24.dp else 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MiuixIconButton(
                                onClick = {
                                    locked = !locked
                                },
                            ) {
                                MiuixIcon(
                                    painter = painterResource(
                                        if (locked) R.drawable.lock_outlined
                                        else R.drawable.lock_open_outlined
                                    ),
                                    contentDescription = if (locked) "解锁" else "锁定操作",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            MiuixIconButton(
                                onClick = {
                                    val activity = context as? android.app.Activity
                                    if (activity != null) {
                                        activity.requestedOrientation = if (isLandscape)
                                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        else
                                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                            ) {
                                MiuixIcon(
                                    painter = painterResource(R.drawable.mobile_rotate_outlined),
                                    contentDescription = "旋转屏幕",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(horizontal = if (isLandscape) 24.dp else 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MiuixIconButton(
                                onClick = {
                                    playerViewRef?.let { saveScreenshot(it, context) }
                                },
                            ) {
                                MiuixIcon(
                                    painter = painterResource(R.drawable.screenshot_keyboard_outlined),
                                    contentDescription = "截屏",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }

                    if (!locked) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                                .padding(horizontal = if (isLandscape) 24.dp else 4.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isVod) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MiuixText(
                                        text = "${formatTime(playerCurrentPosition)} / ${
                                            formatTime(
                                                playerDuration
                                            )
                                        }",
                                        color = Color.White,
                                        style = MiuixTheme.textStyles.body2,
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    PlayerSeekButton(
                                        icon = R.drawable.replay_10_outlined,
                                        text = "后退10s",
                                        onClick = {
                                            exoPlayer.seekTo(
                                                (exoPlayer.currentPosition - 10_000).coerceAtLeast(
                                                    0L
                                                )
                                            )
                                        },
                                    )
                                    PlayerSeekButton(
                                        icon = R.drawable.forward_10_outlined,
                                        text = "前进10s",
                                        onClick = {
                                            exoPlayer.seekTo(
                                                (exoPlayer.currentPosition + 10_000).coerceAtMost(
                                                    playerDuration
                                                )
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
                                    onSeekFinished = {
                                        seekHideJob = scope.launch {
                                            delay(1500)
                                            isSeeking = false
                                        }
                                    },
                                    onSeekingStart = {
                                        seekHideJob?.cancel()
                                        isSeeking = true
                                    },
                                    onSeeking = { fraction ->
                                        seekPreviewPosition = (fraction * playerDuration).toLong()
                                    },
modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }

                        if (isLandscape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    MiuixIconButton(
                                        onClick = {
                                            if (playerDuration > 0 && playerCurrentPosition >= playerDuration - 500) {
                                                exoPlayer.seekTo(0)
                                            }
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        },
                                    ) {
                                        MiuixIcon(
                                            painter = painterResource(
                                                if (playerIsPlaying) R.drawable.pause_filled
                                                else R.drawable.play_filled
                                            ),
                                            contentDescription = if (playerIsPlaying) "暂停" else "播放",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    MiuixIconButton(
                                        onClick = {
                                            toggleMute()
                                        },
                                    ) {
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
                                        text = getQualityLabel(playerVideoWidth, playerVideoHeight),
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MiuixTheme.textStyles.subtitle,
                                    )
                                    Spacer(modifier = Modifier.width(24.dp))
                                    MiuixText(
                                        text = "${currentSpeed}x",
                                        color = Color.White,
                                        style = MiuixTheme.textStyles.subtitle,
                                        modifier = Modifier.clickable {
                                            hideControls()
                                            showSpeedSheet = true
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(24.dp))
                                    MiuixText(
                                        text = getScaleModes(isLandscape).getOrElse(currentScaleModeIndex) { ALL_SCALE_MODES.first() }.label,
                                        color = Color.White,
                                        style = MiuixTheme.textStyles.subtitle,
                                        modifier = Modifier.clickable {
                                            hideControls()
                                            showScaleSheet = true
                                        },
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))
                                    MiuixIconButton(
                                        onClick = {
                                            onFullscreenChange(false)
                                        },
                                    ) {
                                        MiuixIcon(
                                            painter = painterResource(R.drawable.fullscreen_exit_filled),
                                            contentDescription = "关闭全屏",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        MiuixText(
                                            text = getQualityLabel(
                                                playerVideoWidth,
                                                playerVideoHeight
                                            ),
                                            color = Color.White.copy(alpha = 0.5f),
                                            style = MiuixTheme.textStyles.subtitle,
                                        )
                                        Spacer(modifier = Modifier.width(24.dp))
                                        MiuixText(
                                            text = "${currentSpeed}X",
                                            color = Color.White,
                                            style = MiuixTheme.textStyles.subtitle,
                                            modifier = Modifier.clickable {
                                                hideControls()
                                                showSpeedSheet = true
                                            },
                                        )
                                        Spacer(modifier = Modifier.width(24.dp))
                                        MiuixText(
                                            text = getScaleModes(isLandscape).getOrElse(currentScaleModeIndex) { ALL_SCALE_MODES.first() }.label,
                                            color = Color.White,
                                            style = MiuixTheme.textStyles.subtitle,
                                            modifier = Modifier.clickable {
                                                hideControls()
                                                showScaleSheet = true
                                            },
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        MiuixIconButton(
                                            onClick = {
                                                if (playerDuration > 0 && playerCurrentPosition >= playerDuration - 500) {
                                                    exoPlayer.seekTo(0)
                                                }
                                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            },
                                        ) {
                                            MiuixIcon(
                                                painter = painterResource(
                                                    if (playerIsPlaying) R.drawable.pause_filled
                                                    else R.drawable.play_filled
                                                ),
                                                contentDescription = if (playerIsPlaying) "暂停" else "播放",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                        MiuixIconButton(
                                            onClick = {
                                                toggleMute()
                                            },
                                        ) {
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
                                        MiuixIconButton(
                                            onClick = {
                                                onFullscreenChange(false)
                                            },
                                        ) {
                                            MiuixIcon(
                                                painter = painterResource(R.drawable.fullscreen_exit_filled),
                                                contentDescription = "关闭全屏",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!playerIsPlaying && !playerIsBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixIconButton(
                        onClick = {
                            if (playerDuration > 0 && playerCurrentPosition >= playerDuration - 500) {
                                exoPlayer.seekTo(0)
                            }
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            hideControls()
                        },
                        modifier = Modifier.size(64.dp),
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

            PlayerOverlayPanel(
                show = showSpeedSheet,
                title = "播放倍速",
                isLandscape = isLandscape,
                onDismiss = { showSpeedSheet = false },
            ) {
                SpeedPanelContent(
                    currentSpeed = currentSpeed,
                    onSpeedSelect = { speed ->
                        currentSpeed = speed
                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                    },
                    useFixedDarkColors = true,
                )
            }

            PlayerOverlayPanel(
                show = showScaleSheet,
                title = "视频画幅",
                isLandscape = isLandscape,
                onDismiss = { showScaleSheet = false },
            ) {
                ScalePanelContent(
                    selectedIndex = mapPlayerToAllScale(currentScaleModeIndex, isLandscape),
                    onSelect = { index ->
                        val playerIndex = mapAllToPlayerScale(index, isLandscape)
                        currentScaleModeIndex = playerIndex
                        onScaleModeIndexChange?.invoke(index)
                        playerViewRef?.resizeMode =
                            getScaleModes(isLandscape).getOrElse(playerIndex) { ALL_SCALE_MODES.first() }.resizeMode
                    },
                    useFixedDarkColors = true,
                )
            }

            PlayerOverlayPanel(
                show = showMoreSheet,
                title = "更多设置",
                isLandscape = isLandscape,
                onDismiss = { showMoreSheet = false },
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
                    onBackgroundPlaybackChange = { GlobalPlayerConfig.setBackgroundPlayback(context, it) },
                    onAdsFilterChange = { GlobalPlayerConfig.setAdsFilter(context, it) },
                    onPlaybackOrderChange = { GlobalPlayerConfig.setPlaybackOrder(context, it) },
                    onLongPressSpeedChange = { GlobalPlayerConfig.setLongPressSpeed(context, it) },
                    onDismiss = { showMoreSheet = false },
                    useFixedDarkColors = true,
                    showTitle = false,
                    scrollable = false,
                )
            }
        }
    } else {
        NonFullscreenPlayer(
            exoPlayer = exoPlayer,
            currentScaleMode = currentScaleMode,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            playerIsBuffering = playerIsBuffering,
            isSwitchingEpisode = false,
            isVod = isVod,
            playerIsPlaying = playerIsPlaying,
            isMuted = isMuted,
            playerCurrentPosition = playerCurrentPosition,
            playerDuration = playerDuration,
            playerVideoWidth = playerVideoWidth,
            playerVideoHeight = playerVideoHeight,
            currentSpeed = currentSpeed,
            onPlayPause = {
                if (playerDuration > 0 && playerCurrentPosition >= playerDuration - 500) {
                    exoPlayer.seekTo(0)
                }
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            onMuteToggle = {
                toggleMute()
            },
            onFullscreen = {
                onFullscreenChange(true)
            },
            onInteraction = { onInteraction() },
            onPlayerViewRef = { playerViewRef = it },
            onBack = {
                onBack?.invoke()
            },
            onCast = {},
            onAudioPlay = {},
            onMoreSettings = {
                showMoreSheet = true
            },
            showControls = showControls,
            playerBufferedPercentage = playerBufferedPercentage,
            modifier = modifier,
        )
    }

    if (showMoreSheet && !isFullscreen) {
        AppBottomSheet(
            show = showMoreSheet,
            onDismissRequest = { showMoreSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (sheetHeight != null) Modifier.height(sheetHeight) else Modifier)
                    .verticalScroll(rememberScrollState()),
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
                    onBackgroundPlaybackChange = { GlobalPlayerConfig.setBackgroundPlayback(context, it) },
                    onAdsFilterChange = { GlobalPlayerConfig.setAdsFilter(context, it) },
                    onPlaybackOrderChange = { GlobalPlayerConfig.setPlaybackOrder(context, it) },
                    onLongPressSpeedChange = { GlobalPlayerConfig.setLongPressSpeed(context, it) },
                    onDismiss = { showMoreSheet = false },
                    useFixedDarkColors = false,
                    scrollable = false,
                )
            }
        }
    }
}
