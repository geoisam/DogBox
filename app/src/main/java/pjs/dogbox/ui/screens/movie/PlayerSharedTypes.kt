@file:OptIn(ExperimentalLayoutApi::class)

package pjs.dogbox.ui.screens.movie

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pjs.dogbox.R
import pjs.dogbox.data.repository.AppRepository
import pjs.dogbox.ui.components.AppBottomSheet
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun getStatusBarHeight(): Dp {
    val density = LocalDensity.current
    val px = WindowInsets.statusBars.getTop(density)
    return with(density) { px.toDp() }
}

internal class ScaleMode(val label: String, val resizeMode: Int, val aspectRatio: Float? = null)

internal fun getScaleModes(@Suppress("UNUSED_PARAMETER") isLandscape: Boolean): List<ScaleMode> =
    ALL_SCALE_MODES

internal fun ScaleMode.computeSize(
    screenWidthDp: Int,
    screenHeightDp: Int,
    isLandscape: Boolean,
): Pair<Dp, Dp>? {
    val ratio = aspectRatio ?: return null
    val h = screenHeightDp.dp
    val w = (screenHeightDp * ratio).toInt().dp
    return w to h
}

internal val ALL_SCALE_MODES = listOf(
    ScaleMode("原始比例", 0),
    ScaleMode("拉伸填充", 1),
    ScaleMode("自动裁剪", 2),
    ScaleMode("固定等宽", 3),
    ScaleMode("固定登高", 4),
    ScaleMode("16:9", 3, 16f / 9f),
    ScaleMode("9:16", 3, 9f / 16f),
    ScaleMode("4:3", 3, 4f / 3f),
    ScaleMode("3:4", 3, 3f / 4f),
    ScaleMode("3:2", 3, 3f / 2f),
    ScaleMode("2:3", 3, 2f / 3f),
)

internal val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f)

internal fun mapAllToPlayerScale(allIndex: Int, isLandscape: Boolean): Int {
    val mode = ALL_SCALE_MODES.getOrNull(allIndex) ?: return 0
    return getScaleModes(isLandscape).indexOfFirst { it.resizeMode == mode.resizeMode && it.aspectRatio == mode.aspectRatio }
        .coerceAtLeast(0)
}

internal fun mapPlayerToAllScale(playerIndex: Int, isLandscape: Boolean): Int {
    val mode = getScaleModes(isLandscape).getOrNull(playerIndex) ?: return 0
    return ALL_SCALE_MODES.indexOfFirst { it.resizeMode == mode.resizeMode && it.aspectRatio == mode.aspectRatio }
        .coerceAtLeast(0)
}

internal fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

internal fun getQualityLabel(width: Int, height: Int): String {
    val h = height
    return when {
        h >= 4320 -> "8K"
        h >= 2160 -> "4K"
        h >= 1440 -> "2K"
        h >= 1080 -> "1080P"
        h >= 720 -> "720P"
        h >= 480 -> "480P"
        h >= 360 -> "360P"
        else -> "270P"
    }
}

internal fun refreshEndedFrame(exoPlayer: ExoPlayer) {
    runCatching {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            val pos = exoPlayer.duration
            if (pos > 0) {
                exoPlayer.seekTo((pos - 1).coerceAtLeast(0L))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun saveScreenshot(view: PlayerView, context: Context) {
    try {
        when (val videoSurface = view.videoSurfaceView) {
            is SurfaceView if Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                val bitmap = createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                PixelCopy.request(videoSurface, bitmap, { result: Int ->
                    if (result == PixelCopy.SUCCESS) {
                        saveBitmapToGallery(bitmap, context)
                    } else {
                        Toast.makeText(context, "截屏失败", Toast.LENGTH_SHORT).show()
                    }
                }, Handler(Looper.getMainLooper()))
            }

            is TextureView -> {
                val bitmap = videoSurface.bitmap
                if (bitmap != null) {
                    saveBitmapToGallery(bitmap, context)
                } else {
                    Toast.makeText(context, "截屏失败", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                val bitmap = createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                Canvas(bitmap).apply { view.draw(this) }
                saveBitmapToGallery(bitmap, context)
            }
        }
    } catch (_: Exception) {
        Toast.makeText(context, "截屏失败", Toast.LENGTH_SHORT).show()
    }
}

internal fun saveBitmapToGallery(bitmap: Bitmap, context: Context) {
    val filename = "Screenshot_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DogBox")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri =
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let { context.contentResolver.openOutputStream(it) }?.use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        uri?.let { context.contentResolver.update(it, contentValues, null, null) }
    }
    Toast.makeText(context, "截屏已保存至相册", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun SeekBar(
    playedFraction: Float,
    bufferedFraction: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onSeekingStart: () -> Unit = {},
    onSeeking: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val displayFraction = (if (isDragging) dragFraction else playedFraction).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekingStart()
                        onSeek(fraction)
                        onSeeking(fraction)
                        onSeekFinished()
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekingStart()
                        onSeeking(dragFraction)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragFraction)
                        onSeekFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeeking(dragFraction)
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val primaryColor = MiuixTheme.colorScheme.primary

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val w = size.width
            val h = size.height
            val r = h / 2f

            val b = bufferedFraction.coerceIn(0f, 1f)
            val p = displayFraction

            drawRoundRect(Color.White.copy(alpha = 0.15f), Offset.Zero, size, CornerRadius(r))
            if (b > 0f) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.3f),
                    Offset.Zero,
                    Size(w * b, h),
                    CornerRadius(r)
                )
            }
            if (p > 0f) {
                drawRoundRect(primaryColor, Offset.Zero, Size(w * p, h), CornerRadius(r))
            }
            drawCircle(primaryColor, 6.dp.toPx(), Offset(w * p, h / 2f))
        }
    }
}

@Composable
internal fun SpeedResetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFixedDarkColors: Boolean = false,
) {
    val containerColor = if (useFixedDarkColors) Color.White.copy(alpha = 0.08f)
    else MiuixTheme.colorScheme.surfaceContainerHighest
    val textColor = if (useFixedDarkColors) Color.White
    else MiuixTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixText(
                text = "恢复默认",
                style = MiuixTheme.textStyles.subtitle,
                color = textColor,
            )
        }
    }
}

internal val SPEED_PRESETS = listOf(0.50f, 0.75f, 1.25f, 1.50f, 2.00f, 3.00f)

internal val SPEED_KEY_POINTS = List(20) { (it + 1) * 0.25f }

@Composable
internal fun PlayerFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    useFixedDarkColors: Boolean = false,
) {
    val containerColor = when {
        selected -> MiuixTheme.colorScheme.primaryContainer
        useFixedDarkColors -> Color.Transparent
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        selected -> MiuixTheme.colorScheme.onPrimaryContainer
        useFixedDarkColors -> Color.White
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
    }
    val borderColor = when {
        selected -> MiuixTheme.colorScheme.primary
        useFixedDarkColors -> Color.White.copy(alpha = 0.6f)
        else -> MiuixTheme.colorScheme.dividerLine
    }
    Card(
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = onClick,
    ) {
        MiuixText(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun SpeedPanelContent(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    useFixedDarkColors: Boolean,
    modifier: Modifier = Modifier,
) {
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    val cardContainer = if (useFixedDarkColors) Color.White.copy(alpha = 0.08f)
    else MiuixTheme.colorScheme.surfaceContainerHigh
    val titleColor = if (useFixedDarkColors) Color.White
    else MiuixTheme.colorScheme.onSurface
    val valueColor = if (useFixedDarkColors) Color.White.copy(alpha = 0.6f)
    else MiuixTheme.colorScheme.onSurfaceSecondary

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SPEED_PRESETS.forEach { preset ->
                PlayerFilterChip(
                    selected = abs(currentSpeed - preset) < 0.001f,
                    onClick = {
                        sliderSpeed = preset
                        onSpeedSelect(preset)
                    },
                    label = "${"%.2f".format(preset)}x",
                    useFixedDarkColors = useFixedDarkColors,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.defaultColors(color = cardContainer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = "固定倍速",
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                        color = titleColor,
                    )
                    MiuixText(
                        text = "${"%.2f".format(sliderSpeed)}X",
                        style = MiuixTheme.textStyles.footnote1,
                        color = valueColor,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = sliderSpeed,
                    onValueChange = { sliderSpeed = (it * 4).toInt() / 4f },
                    onValueChangeFinished = { onSpeedSelect(sliderSpeed) },
                    valueRange = 0.25f..5.0f,
                    steps = 18,
                    showKeyPoints = true,
                    keyPoints = SPEED_KEY_POINTS,
                    magnetThreshold = 0.03f,
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SpeedResetButton(
            onClick = {
                sliderSpeed = 1.0f
                onSpeedSelect(1.0f)
            },
            modifier = Modifier.padding(horizontal = 12.dp),
            useFixedDarkColors = useFixedDarkColors,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun SpeedBottomSheet(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
    height: Dp? = null,
) {
    AppBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (height != null) Modifier.height(height) else Modifier)
                .verticalScroll(rememberScrollState()),
        ) {
            SpeedPanelContent(
                currentSpeed = currentSpeed,
                onSpeedSelect = onSpeedSelect,
                useFixedDarkColors = false,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun ScalePanelContent(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    useFixedDarkColors: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ALL_SCALE_MODES.forEachIndexed { index, scaleMode ->
                PlayerFilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    label = scaleMode.label,
                    useFixedDarkColors = useFixedDarkColors,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ScaleBottomSheet(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    height: Dp? = null,
) {
    AppBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (height != null) Modifier.height(height) else Modifier)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            ScalePanelContent(
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                useFixedDarkColors = false,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * 全屏播放时使用的固定深色 overlay 面板，代替底部弹层。
 *
 * - 横屏：从右侧滑入，占屏宽 50%，背景沿“右→左”由黑渐透明。
 * - 竖屏：从底部滑入，占屏高 50%，背景沿“下→上”由黑渐透明。
 * - 顶部最左为标题，最右为关闭按钮，下方内容与原来弹层一致。
 */
@Composable
internal fun PlayerOverlayPanel(
    show: Boolean,
    title: String,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentScrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scrimBrush = if (isLandscape) {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
        )
    }
    val panelBrush = if (isLandscape) {
        Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f)),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f)),
        )
    }
    val panelShape = if (isLandscape) {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = show,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimBrush)
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }

        AnimatedVisibility(
            visible = show,
            enter = if (isLandscape) {
                slideInHorizontally(animationSpec = tween(250)) { it }
            } else {
                slideInVertically(animationSpec = tween(250)) { it }
            },
            exit = if (isLandscape) {
                slideOutHorizontally(animationSpec = tween(250)) { it }
            } else {
                slideOutVertically(animationSpec = tween(250)) { it }
            },
            modifier = if (isLandscape) {
                Modifier.align(Alignment.CenterEnd)
            } else {
                Modifier.align(Alignment.BottomCenter)
            },
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (isLandscape) {
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.5f)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                        }
                    )
                    .clip(panelShape)
                    .background(panelBrush)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.title4,
                        color = Color.White,
                    )
                    MiuixIconButton(onClick = onDismiss) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.close_outlined),
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(if (contentScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                ) {
                    content()
                }
            }
        }
    }
}

internal enum class PlaybackOrder(val label: String) {
    AUTO_NEXT("自动连播"),
    STOP_AFTER("播完暂停"),
    SINGLE_LOOP("单个循环"),
    LIST_LOOP("列表循环"),
}

internal object GlobalPlayerConfig {
    private var initialized = false

    var playbackOrder by mutableStateOf(PlaybackOrder.AUTO_NEXT)
        private set

    var backgroundPlayback by mutableStateOf(false)
        private set

    var adsFilter by mutableStateOf(true)
        private set

    var longPressSpeed by mutableStateOf(3.0f)
        private set

    fun init(context: Context) {
        if (initialized) return
        playbackOrder = PlaybackOrder.entries.getOrElse(
            AppRepository.getPlaybackOrder(context)
        ) { PlaybackOrder.AUTO_NEXT }
        backgroundPlayback = AppRepository.isBackgroundPlayback(context)
        adsFilter = AppRepository.isAdsFilterEnabled(context)
        longPressSpeed = AppRepository.getLongPressSpeed(context)
        initialized = true
    }

    fun setBackgroundPlayback(context: Context, enabled: Boolean) {
        backgroundPlayback = enabled
        AppRepository.setBackgroundPlayback(context, enabled)
    }

    fun setAdsFilter(context: Context, enabled: Boolean) {
        adsFilter = enabled
        AppRepository.setAdsFilterEnabled(context, enabled)
    }

    fun setPlaybackOrder(context: Context, order: PlaybackOrder) {
        playbackOrder = order
        AppRepository.setPlaybackOrder(context, order.ordinal)
    }

    fun setLongPressSpeed(context: Context, speed: Float) {
        longPressSpeed = speed
        AppRepository.setLongPressSpeed(context, speed)
    }
}

@Composable
private fun PlaybackOrderDropdown(
    playbackOrder: PlaybackOrder,
    onPlaybackOrderChange: (PlaybackOrder) -> Unit,
    useFixedDarkColors: Boolean,
) {
    val dropdown: @Composable () -> Unit = {
        OverlayDropdownPreference(
            title = "播放顺序",
            items = PlaybackOrder.entries.map { it.label },
            selectedIndex = PlaybackOrder.entries
                .indexOf(playbackOrder)
                .coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                onPlaybackOrderChange(PlaybackOrder.entries[index])
            },
        )
    }
    if (useFixedDarkColors) {
        val fixedDarkColors = remember {
            darkColorScheme(
                onSurface = Color.White,
                onSurfaceVariantActions = Color.White.copy(alpha = 0.6f),
            )
        }
        MiuixTheme(colors = fixedDarkColors, content = dropdown)
    } else {
        dropdown()
    }
}

@Composable
internal fun MoreSettingsContent(
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    backgroundPlayback: Boolean,
    adsFilter: Boolean,
    playbackOrder: PlaybackOrder,
    longPressSpeed: Float = 3.0f,
    onFlipHorizontalChange: (Boolean) -> Unit,
    onFlipVerticalChange: (Boolean) -> Unit,
    onBackgroundPlaybackChange: (Boolean) -> Unit,
    onAdsFilterChange: (Boolean) -> Unit,
    onPlaybackOrderChange: (PlaybackOrder) -> Unit,
    onLongPressSpeedChange: (Float) -> Unit = {},
    onDismiss: () -> Unit,
    useFixedDarkColors: Boolean = true,
    showTitle: Boolean = true,
    scrollable: Boolean = true,
) {
    val titleColor = if (useFixedDarkColors) Color.White
    else MiuixTheme.colorScheme.onSurface

    val cardContainer = if (useFixedDarkColors) Color.White.copy(alpha = 0.08f)
    else MiuixTheme.colorScheme.surfaceContainerHigh
    val rowValueColor = if (useFixedDarkColors) Color.White.copy(alpha = 0.6f)
    else MiuixTheme.colorScheme.onSurfaceSecondary

    Column(modifier = Modifier.fillMaxWidth()) {
        if (useFixedDarkColors && showTitle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText("更多设置", style = MiuixTheme.textStyles.title4, color = titleColor)
                MiuixIconButton(onClick = onDismiss) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.close_outlined),
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // ===== 卡片一：播放设置 =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.defaultColors(color = cardContainer),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 画面翻转
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixText(
                            text = "画面翻转",
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.body1,
                            color = titleColor,
                        )
                        PlayerFilterChip(
                            selected = flipHorizontal,
                            onClick = { onFlipHorizontalChange(!flipHorizontal) },
                            label = "左右翻转",
                            useFixedDarkColors = useFixedDarkColors,
                        )
                        Spacer(Modifier.width(8.dp))
                        PlayerFilterChip(
                            selected = flipVertical,
                            onClick = { onFlipVerticalChange(!flipVertical) },
                            label = "上下翻转",
                            useFixedDarkColors = useFixedDarkColors,
                        )
                    }
                    // 播放顺序
                    PlaybackOrderDropdown(
                        playbackOrder = playbackOrder,
                        onPlaybackOrderChange = onPlaybackOrderChange,
                        useFixedDarkColors = useFixedDarkColors,
                    )
                    // 后台播放
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixText(
                            text = "后台播放",
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.body1,
                            color = titleColor,
                        )
                        Switch(
                            checked = backgroundPlayback,
                            onCheckedChange = onBackgroundPlaybackChange,
                        )
                    }
                    // 广告过滤
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixText(
                            text = "广告过滤",
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.body1,
                            color = titleColor,
                        )
                        Switch(
                            checked = adsFilter,
                            onCheckedChange = onAdsFilterChange,
                        )
                    }
                    // 定时关闭
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixText(
                            text = "定时关闭",
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.body1,
                            color = titleColor,
                        )
                        MiuixText(
                            text = "未开启",
                            style = MiuixTheme.textStyles.body2,
                            color = rowValueColor,
                        )
                        Spacer(Modifier.size(6.dp))
                        MiuixIcon(
                            painter = painterResource(R.drawable.keyboard_arrow_right_outlined),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = rowValueColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 卡片二：长按倍速 =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.defaultColors(color = cardContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixText(
                            text = "长按倍速",
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.body1,
                            color = titleColor,
                        )
                        MiuixText(
                            text = "${"%.2f".format(longPressSpeed)}X",
                            style = MiuixTheme.textStyles.body2,
                            color = rowValueColor,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = longPressSpeed,
                        onValueChange = { onLongPressSpeedChange((it * 4).toInt() / 4f) },
                        valueRange = 0.25f..5.0f,
                        steps = 18,
                        showKeyPoints = true,
                        keyPoints = SPEED_KEY_POINTS,
                        magnetThreshold = 0.03f,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    )
                    Spacer(Modifier.height(4.dp))
                    SpeedResetButton(
                        onClick = { onLongPressSpeedChange(3.0f) },
                        modifier = Modifier.align(Alignment.End),
                        useFixedDarkColors = useFixedDarkColors,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerCore(
    exoPlayer: ExoPlayer,
    currentScaleMode: ScaleMode,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    playerIsBuffering: Boolean,
    isRewinding: Boolean,
    isFastForwarding: Boolean,
    isAdjustingBrightness: Boolean,
    isAdjustingVolume: Boolean,
    brightnessPercent: Int,
    volumePercent: Int,
    isSeeking: Boolean,
    seekPreviewPosition: Long,
    playerDuration: Long,
    isLandscape: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int,
    onInteraction: () -> Unit,
    onPlayerViewRef: (PlayerView) -> Unit,
    window: Window?,
    audioManager: AudioManager,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isRewindingChange: (Boolean) -> Unit,
    isFastForwardingChange: (Boolean) -> Unit,
    brightnessHideJob: Job?,
    onBrightnessHideJobChange: (Job) -> Unit,
    volumeHideJob: Job?,
    onVolumeHideJobChange: (Job) -> Unit,
    seekHideJob: Job?,
    onSeekHideJobChange: (Job) -> Unit,
    isSeekingChange: (Boolean) -> Unit,
    seekPreviewPositionChange: (Long) -> Unit,
    isAdjustingBrightnessChange: (Boolean) -> Unit,
    isAdjustingVolumeChange: (Boolean) -> Unit,
    onVolumePercentChange: (Int) -> Unit,
    scope: CoroutineScope,
    resetControlsTimer: () -> Unit,
    showControls: Boolean,
    isSwitchingEpisode: Boolean = false,
    playerIsPlaying: Boolean = false,
    backgroundColor: Color = Color.Black,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
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
                    runCatching {
                        player = exoPlayer
                        resizeMode = currentScaleMode.resizeMode
                        refreshEndedFrame(exoPlayer)
                    }
                }.also {
                    onPlayerViewRef(it)
                }
            },
            update = { playerView ->
                playerView.resizeMode = currentScaleMode.resizeMode
                playerView.scaleX = if (flipHorizontal) -1f else 1f
                playerView.scaleY = if (flipVertical) -1f else 1f
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

    if (playerIsBuffering || isSwitchingEpisode) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(size = 56.dp)
        }
    }

    if (isRewinding || isFastForwarding) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (screenHeightDp / 4f).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                MiuixText(
                    text = if (isFastForwarding) "3.0x倍速前进" else "3.0x倍速后退",
                    color = Color.White,
                    style = MiuixTheme.textStyles.title4,
                )
            }
        }
    }

    if (isAdjustingBrightness || isAdjustingVolume) {
        val percent = if (isAdjustingBrightness) brightnessPercent else volumePercent
        val icon = when {
            isAdjustingBrightness -> when {
                percent == 0 -> R.drawable.brightness_empty_outlined
                percent <= 33 -> R.drawable.brightness_5_outlined
                percent <= 66 -> R.drawable.brightness_6_outlined
                else -> R.drawable.brightness_7_outlined
            }

            else -> when {
                percent == 0 -> R.drawable.volume_off_outlined
                percent <= 33 -> R.drawable.volume_mute_outlined
                percent <= 66 -> R.drawable.volume_down_outlined
                else -> R.drawable.volume_up_outlined
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 48.dp, vertical = 24.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MiuixIcon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MiuixText(
                        text = "${percent}%",
                        color = Color.White,
                        style = MiuixTheme.textStyles.title4,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = percent / 100f)
                                .fillMaxHeight()
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }

    if (isSeeking && playerDuration > 0) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (screenHeightDp / 4f).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                MiuixText(
                    text = "${formatTime(seekPreviewPosition)} / ${formatTime(playerDuration)}",
                    color = Color.White,
                    style = MiuixTheme.textStyles.title4,
                )
            }
        }
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }
    val viewConfig = LocalViewConfiguration.current

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    val w = size.width.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position
                        down.consume()

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
                        var dragAxis = 0 // 0=none,1=h,2=v
                        var lastPos = downPos

                        while (true) {
                            if (!longPressFired && !pastTouchSlop) {
                                val elapsed = System.currentTimeMillis() - downTime
                                if (elapsed >= viewConfig.longPressTimeoutMillis) {
                                    longPressFired = true
                                    isRewindingChange(true)
                                }
                            }

                            val remaining = if (!longPressFired && !pastTouchSlop) {
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
                                if (!longPressFired && !pastTouchSlop) {
                                    longPressFired = true
                                    isRewindingChange(true)
                                }
                                continue
                            }

                            val change = event.changes.first()
                            if (!change.pressed) {
                                if (longPressFired) {
                                    isRewindingChange(false)
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
                                val dx = change.position.x - downPos.x
                                val dy = change.position.y - downPos.y
                                if (abs(dx) > viewConfig.touchSlop || abs(dy) > viewConfig.touchSlop) {
                                    pastTouchSlop = true
                                    if (downPos.x < 32.dp.toPx() || downPos.y < 32.dp.toPx() || downPos.y > size.height - 32.dp.toPx()) {
                                        dragAxis = 0
                                    } else {
                                        dragAxis =
                                            if (abs(dx) > abs(dy)) 1 else 2
                                        if (dragAxis == 2) {
                                            brightnessHideJob?.cancel()
                                            volumeHideJob?.cancel()
                                            isAdjustingBrightnessChange(true)
                                            isAdjustingVolumeChange(false)
                                        } else {
                                            seekHideJob?.cancel()
                                            seekPreviewPositionChange(exoPlayer.currentPosition)
                                            isSeekingChange(true)
                                        }
                                    }
                                }
                            }

                            if (pastTouchSlop) {
                                change.consume()
                                val delta = change.position - lastPos
                                if (dragAxis == 2) {
                                    @Suppress("DEPRECATION")
                                    val lp = window?.attributes ?: continue
                                    val b = (lp.screenBrightness - delta.y / 300f).coerceIn(0f, 1f)
                                    lp.screenBrightness = b
                                    window.attributes = lp
                                    onBrightnessChange(b)
                                } else if (dragAxis == 1 && playerDuration > 0) {
                                    val seekDelta = (delta.x / w * playerDuration).toLong()
                                    val newPos = (exoPlayer.currentPosition + seekDelta).coerceIn(
                                        0,
                                        playerDuration
                                    )
                                    exoPlayer.seekTo(newPos)
                                    seekPreviewPositionChange(newPos)
                                }
                            }

                            lastPos = change.position
                        }

                        if (pastTouchSlop) {
                            if (dragAxis == 2) {
                                val job =
                                    scope.launch { delay(1500); isAdjustingBrightnessChange(false) }
                                onBrightnessHideJobChange(job)
                            } else if (dragAxis == 1) {
                                resetControlsTimer()
                                val job = scope.launch { delay(1500); isSeekingChange(false) }
                                onSeekHideJobChange(job)
                            }
                        }
                    }
                },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    val w = size.width.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position
                        down.consume()

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
                        var dragAxis = 0
                        var lastPos = downPos

                        while (true) {
                            if (!longPressFired && !pastTouchSlop) {
                                val elapsed = System.currentTimeMillis() - downTime
                                if (elapsed >= viewConfig.longPressTimeoutMillis) {
                                    longPressFired = true
                                    isFastForwardingChange(true)
                                }
                            }

                            val remaining = if (!longPressFired && !pastTouchSlop) {
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
                                if (!longPressFired && !pastTouchSlop) {
                                    longPressFired = true
                                    isFastForwardingChange(true)
                                }
                                continue
                            }

                            val change = event.changes.first()
                            if (!change.pressed) {
                                if (longPressFired) {
                                    isFastForwardingChange(false)
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
                                val dx = change.position.x - downPos.x
                                val dy = change.position.y - downPos.y
                                if (abs(dx) > viewConfig.touchSlop || abs(dy) > viewConfig.touchSlop) {
                                    pastTouchSlop = true
                                    if (downPos.x > size.width - 32.dp.toPx() || downPos.y < 32.dp.toPx() || downPos.y > size.height - 32.dp.toPx()) {
                                        dragAxis = 0
                                    } else {
                                        dragAxis =
                                            if (abs(dx) > abs(dy)) 1 else 2
                                        if (dragAxis == 2) {
                                            volumeHideJob?.cancel()
                                            brightnessHideJob?.cancel()
                                            isAdjustingVolumeChange(true)
                                            isAdjustingBrightnessChange(false)
                                            val maxVol =
                                                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val curVol =
                                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            onVolumePercentChange((curVol * 100 / maxVol))
                                        } else {
                                            seekHideJob?.cancel()
                                            seekPreviewPositionChange(exoPlayer.currentPosition)
                                            isSeekingChange(true)
                                        }
                                    }
                                }
                            }

                            if (pastTouchSlop) {
                                change.consume()
                                val delta = change.position - lastPos
                                if (dragAxis == 2) {
                                    val maxVol =
                                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val newVol =
                                        (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - (delta.y / 300f * maxVol).toInt()).coerceIn(
                                            0,
                                            maxVol
                                        )
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        newVol,
                                        0
                                    )
                                    onVolumePercentChange((newVol * 100 / maxVol))
                                } else if (dragAxis == 1 && playerDuration > 0) {
                                    val seekDelta = (delta.x / w * playerDuration).toLong()
                                    val newPos = (exoPlayer.currentPosition + seekDelta).coerceIn(
                                        0,
                                        playerDuration
                                    )
                                    exoPlayer.seekTo(newPos)
                                    seekPreviewPositionChange(newPos)
                                }
                            }

                            lastPos = change.position
                        }

                        if (pastTouchSlop) {
                            if (dragAxis == 2) {
                                val job =
                                    scope.launch { delay(1500); isAdjustingVolumeChange(false) }
                                onVolumeHideJobChange(job)
                            } else if (dragAxis == 1) {
                                resetControlsTimer()
                                val job = scope.launch { delay(1500); isSeekingChange(false) }
                                onSeekHideJobChange(job)
                            }
                        }
                    }
                },
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerSeekButton(
    icon: Int,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MiuixIcon(
            painter = painterResource(icon),
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        MiuixText(
            text = text,
            color = Color.White,
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun NonFullscreenPlayer(
    exoPlayer: ExoPlayer,
    currentScaleMode: ScaleMode,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    playerIsBuffering: Boolean,
    isSwitchingEpisode: Boolean,
    isVod: Boolean,
    playerIsPlaying: Boolean,
    isMuted: Boolean,
    playerCurrentPosition: Long,
    playerDuration: Long,
    playerVideoWidth: Int,
    playerVideoHeight: Int,
    currentSpeed: Float,
    onPlayPause: () -> Unit,
    onMuteToggle: () -> Unit,
    onFullscreen: () -> Unit,
    onInteraction: () -> Unit,
    onPlayerViewRef: (PlayerView) -> Unit,
    onBack: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    onAudioPlay: (() -> Unit)? = null,
    onMoreSettings: (() -> Unit)? = null,
    showControls: Boolean = true,
    playerBufferedPercentage: Int = 0,
    modifier: Modifier = Modifier,
) {
    var bgColorIndex by remember { mutableIntStateOf(0) }
    val bgColors = listOf(Color.Black, Color.White, Color.Gray)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(bgColors[bgColorIndex]),
    ) {
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
                        runCatching {
                            player = exoPlayer
                            resizeMode = currentScaleMode.resizeMode
                            refreshEndedFrame(exoPlayer)
                        }
                    }.also { onPlayerViewRef(it) }
                },
                update = { playerView ->
                    playerView.resizeMode = currentScaleMode.resizeMode
                    playerView.scaleX = if (flipHorizontal) -1f else 1f
                    playerView.scaleY = if (flipVertical) -1f else 1f
                },
                onRelease = { runCatching { it.player = null } },
                modifier = (currentScaleMode.aspectRatio?.let { ratio ->
                    val h = 240.dp
                    val w = (240f * ratio).dp
                    Modifier
                        .width(w)
                        .height(h)
                } ?: Modifier.fillMaxSize()),
            )
        }

        if (playerIsBuffering || isSwitchingEpisode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(size = 56.dp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onInteraction() },
                        onDoubleTap = { onFullscreen() },
                    )
                },
        )

        if (!playerIsPlaying && !playerIsBuffering && !isSwitchingEpisode) {
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
                        onInteraction()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            CircleShape
                        ),
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

        if (showControls) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (onBack != null) {
                        MiuixIconButton(onClick = onBack) {
                            MiuixIcon(
                                painter = painterResource(R.drawable.arrow_back_outlined),
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (onAudioPlay != null) {
                        MiuixIconButton(onClick = {
                            bgColorIndex = (bgColorIndex + 1) % bgColors.size
                            onAudioPlay()
                        }) {
                            MiuixIcon(
                                painter = painterResource(R.drawable.sync_alt_outlined),
                                contentDescription = "切换背景色",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (onCast != null) {
                        MiuixIconButton(onClick = onCast) {
                            MiuixIcon(
                                painter = painterResource(R.drawable.cast_outlined),
                                contentDescription = "投屏",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (onMoreSettings != null) {
                        MiuixIconButton(onClick = onMoreSettings) {
                            MiuixIcon(
                                painter = painterResource(R.drawable.more_horiz_filled),
                                contentDescription = "更多设置",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (isVod) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(
                                text = "${formatTime(playerCurrentPosition)} / ${
                                    formatTime(
                                        playerDuration
                                    )
                                }",
                                color = Color.White,
                                style = MiuixTheme.textStyles.footnote2,
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
                            onSeekFinished = {},
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        MiuixIconButton(onClick = onPlayPause) {
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
                        MiuixIconButton(onClick = onMuteToggle) {
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
                        MiuixIconButton(onClick = onFullscreen) {
                            MiuixIcon(
                                painter = painterResource(R.drawable.fullscreen_filled),
                                contentDescription = "全屏",
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

@Composable
internal fun EpisodePanelContent(
    episodes: List<EpisodeInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    useFixedDarkColors: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pageSize = 100
    val totalEpisodes = episodes.size
    val categoryCount = (totalEpisodes + pageSize - 1) / pageSize
    val hasCategories = categoryCount > 1
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val categoryTabs = remember(categoryCount, totalEpisodes) {
        (0 until categoryCount).map { catIdx ->
            val start = catIdx * pageSize + 1
            val end = minOf((catIdx + 1) * pageSize, totalEpisodes)
            "$start-$end"
        }
    }
    val categoryListState = rememberLazyListState()

    val pagerState = rememberPagerState(
        pageCount = { categoryCount.coerceAtLeast(1) },
        initialPage = if (hasCategories) selectedIndex / pageSize else 0,
    )

    LaunchedEffect(selectedIndex) {
        withFrameNanos { }
        repeat(10) {
            bringIntoViewRequester.bringIntoView()
            withFrameNanos { }
        }
        if (hasCategories) {
            val targetPage = (selectedIndex / pageSize).coerceIn(0, categoryCount - 1)
            if (targetPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (hasCategories) {
            categoryListState.animateScrollToItem(pagerState.currentPage)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasCategories) {
            LazyRow(
                state = categoryListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(categoryTabs) { index, label ->
                    val selected = pagerState.currentPage == index
                    val chipColor by animateColorAsState(
                        targetValue = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else if (useFixedDarkColors) {
                            Color.White.copy(alpha = 0.08f)
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = tween(200),
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (selected) {
                            MiuixTheme.colorScheme.onPrimary
                        } else if (useFixedDarkColors) {
                            Color.White.copy(alpha = 0.6f)
                        } else {
                            MiuixTheme.colorScheme.onSurfaceSecondary
                        },
                        animationSpec = tween(200),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(chipColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        MiuixText(
                            text = label,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                EpisodeChipGrid(
                    episodes = episodes.subList(
                        page * pageSize,
                        minOf((page + 1) * pageSize, totalEpisodes)
                    ),
                    startIndex = page * pageSize,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    useFixedDarkColors = useFixedDarkColors,
                    bringIntoViewRequester = bringIntoViewRequester,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            EpisodeChipGrid(
                episodes = episodes,
                startIndex = 0,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                useFixedDarkColors = useFixedDarkColors,
                bringIntoViewRequester = bringIntoViewRequester,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EpisodeChipGrid(
    episodes: List<EpisodeInfo>,
    startIndex: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    useFixedDarkColors: Boolean,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            episodes.forEachIndexed { idx, ep ->
                val realIdx = startIndex + idx
                PlayerFilterChip(
                    selected = realIdx == selectedIndex,
                    onClick = { onSelect(realIdx) },
                    label = ep.name,
                    useFixedDarkColors = useFixedDarkColors,
                    modifier = if (realIdx == selectedIndex) {
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