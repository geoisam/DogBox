package pjs.dogbox.ui.screen.discover

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.ui.screen.movie.ScaleBottomSheet
import pjs.dogbox.ui.screen.movie.SpeedBottomSheet
import pjs.dogbox.ui.screen.movie.VideoPlayerScreen

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaLinkTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isDetecting by remember { mutableStateOf(false) }
    var isVod by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val notice = "支持.m3u8、.mp4、.flv等点播或直播"

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
        focusManager.clearFocus()
        val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
        if (fullscreen) {
            act.requestedOrientation = if (playerVideoWidth >= playerVideoHeight)
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

    DisposableEffect(exoPlayer) {
        val errorListener = object : Player.Listener {
            override fun onPlayerError(pError: PlaybackException) {
                isPlaying = false
                isDetecting = false
                if (isFullscreen) {
                    setFullscreen(false)
                }
            }
        }
        exoPlayer.addListener(errorListener)
        onDispose {
            exoPlayer.removeListener(errorListener)
            exoPlayer.release()
        }
    }

    fun detectAndPlay(inputUrl: String) {
        if (inputUrl.isBlank()) {
            Toast.makeText(context, "请输入播放链接", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                isDetecting = true
                var ready = false
                var error = false
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) ready = true
                    }

                    override fun onPlayerError(pError: PlaybackException) {
                        error = true
                    }
                }
                exoPlayer.addListener(listener)
                exoPlayer.stop()
                exoPlayer.setMediaItem(MediaItem.fromUri(inputUrl))
                exoPlayer.prepare()

                val timeout = 10000L
                val startMs = System.currentTimeMillis()
                while (!ready && !error && System.currentTimeMillis() - startMs < timeout) {
                    delay(100L)
                }

                exoPlayer.removeListener(listener)

                if (error || !ready) {
                    isDetecting = false
                    exoPlayer.stop()
                    Toast.makeText(context, "链接无法播放", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val isLive = exoPlayer.isCurrentMediaItemLive || exoPlayer.duration <= 0
                isVod = !isLive
                isDetecting = false
                exoPlayer.playWhenReady = true
                isPlaying = true
            } catch (e: Exception) {
                isDetecting = false
                isPlaying = false
                exoPlayer.stop()
                Toast.makeText(context, "播放失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        if (isFullscreen) {
            setFullscreen(false)
        } else if (isPlaying) {
            exoPlayer.stop()
            isPlaying = false
            onBack()
        } else {
            onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isPlaying && isFullscreen) {
            VideoPlayerScreen(
                exoPlayer = exoPlayer,
                title = url,
                isVod = isVod,
                isFullscreen = true,
                onFullscreenChange = { setFullscreen(it) },
                onBack = {
                    exoPlayer.stop()
                    isPlaying = false
                },
                scaleModeIndex = currentScaleModeIndex,
                onScaleModeIndexChange = { currentScaleModeIndex = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                if (isPlaying) {
                    VideoPlayerScreen(
                        exoPlayer = exoPlayer,
                        title = url,
                        isVod = isVod,
                        isFullscreen = false,
                        onFullscreenChange = { setFullscreen(it) },
                        onBack = {
                            exoPlayer.stop()
                            isPlaying = false
                        },
                        scaleModeIndex = currentScaleModeIndex,
                        onScaleModeIndexChange = { currentScaleModeIndex = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
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
                        if (isDetecting) {
                            ChrysanthemumLoading(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.autoplay_outlined),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(56.dp)
                                    .align(Alignment.Center),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

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
                                    text = notice,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("播放链接") },
                            placeholder = { Text("请输入播放链接") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            maxLines = 100,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    detectAndPlay(url.trim())
                                }
                            ),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ActionIconItem(
                                icon = R.drawable.link_outlined,
                                label = "过滤链接",
                                onClick = {
                                    focusManager.clearFocus()
                                    val link = extractShareUrl(url)
                                    if (link != null) {
                                        url = link
                                    } else {
                                        Toast.makeText(context, "未识别到链接", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.kid_star_outlined,
                                label = "收藏视频",
                                onClick = {
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.cloud_download_outlined,
                                label = "缓存视频",
                                onClick = {
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.speed_outlined,
                                label = "播放倍速",
                                onClick = {
                                    focusManager.clearFocus()
                                    showSpeedSheet = true
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ActionIconItem(
                                icon = R.drawable.aspect_ratio_outlined,
                                label = "视频画幅",
                                onClick = {
                                    focusManager.clearFocus()
                                    showScaleSheet = true
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        ClipboardActionRow(
                            onClear = {
                                focusManager.clearFocus()
                                url = ""
                            },
                            onPaste = {
                                focusManager.clearFocus()
                                val pasted = pasteFromClipboard(context)
                                if (pasted != null) {
                                    url = pasted
                                } else {
                                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FilledTonalButton(
                            onClick = {
                                focusManager.clearFocus()
                                detectAndPlay(url.trim())
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                "开始播放",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                    }
                }
            }
        }
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
