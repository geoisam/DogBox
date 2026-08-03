@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package pjs.dogbox.ui.screen.movie

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
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading

@Composable
fun getStatusBarHeight(): Dp {
    val density = LocalDensity.current
    val px = WindowInsets.statusBars.getTop(density)
    return with(density) { px.toDp() }
}

internal class ScaleMode(val label: String, val resizeMode: Int, val aspectRatio: Float? = null)

internal fun getScaleModes(@Suppress("UNUSED_PARAMETER") isLandscape: Boolean): List<ScaleMode> = ALL_SCALE_MODES

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
    return getScaleModes(isLandscape).indexOfFirst { it.resizeMode == mode.resizeMode && it.aspectRatio == mode.aspectRatio }.coerceAtLeast(0)
}

internal fun mapPlayerToAllScale(playerIndex: Int, isLandscape: Boolean): Int {
    val mode = getScaleModes(isLandscape).getOrNull(playerIndex) ?: return 0
    return ALL_SCALE_MODES.indexOfFirst { it.resizeMode == mode.resizeMode && it.aspectRatio == mode.aspectRatio }.coerceAtLeast(0)
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
        val primaryColor = MaterialTheme.colorScheme.primary

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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "恢复默认",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun SpeedBottomSheet(
    currentSpeed: Float,
    longPressSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onLongPressSpeedConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
    fixedHeight: Dp,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var sliderLongPressSpeed by remember(longPressSpeed) { mutableFloatStateOf(longPressSpeed) }

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
                .height(fixedHeight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("播放倍速", style = MaterialTheme.typography.titleMedium)
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
            Spacer(Modifier.height(16.dp))
            Text(
                "固定倍速：${"%.2f".format(sliderSpeed)}X",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderSpeed,
                onValueChange = { sliderSpeed = (it * 4).toInt() / 4f },
                onValueChangeFinished = { onSpeedSelect(sliderSpeed) },
                valueRange = 0.25f..5.0f,
                steps = 18,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            SpeedResetButton(
                onClick = {
                    sliderSpeed = 1.0f
                    onSpeedSelect(1.0f)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "长按前进/后退倍速：${"%.2f".format(sliderLongPressSpeed)}X",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderLongPressSpeed,
                onValueChange = { sliderLongPressSpeed = (it * 4).toInt() / 4f },
                onValueChangeFinished = { onLongPressSpeedConfirm(sliderLongPressSpeed) },
                valueRange = 0.25f..5.0f,
                steps = 18,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            SpeedResetButton(
                onClick = {
                    sliderLongPressSpeed = 3.0f
                    onLongPressSpeedConfirm(3.0f)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun ScaleBottomSheet(
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
                .height(fixedHeight)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("视频画幅", style = MaterialTheme.typography.titleMedium)
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
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ALL_SCALE_MODES.forEachIndexed { index, scaleMode ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index); onDismiss() },
                        label = { Text(scaleMode.label) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun PlayerOverlay(
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }

    fun dismiss() {
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(200)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { dismiss() },
            ),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (isLandscape) slideInHorizontally { it } else slideInVertically { it },
            exit = if (isLandscape) slideOutHorizontally { it } else slideOutVertically { it },
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (isLandscape) Modifier.fillMaxHeight().width(LocalConfiguration.current.screenWidthDp.dp * 0.5f)
                        else Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp * 0.5f)
                    )
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                content()
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

@Composable
internal fun MoreSettingsContent(
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    backgroundPlayback: Boolean,
    playbackOrder: PlaybackOrder,
    onFlipHorizontalChange: (Boolean) -> Unit,
    onFlipVerticalChange: (Boolean) -> Unit,
    onBackgroundPlaybackChange: (Boolean) -> Unit,
    onPlaybackOrderChange: (PlaybackOrder) -> Unit,
    onDismiss: () -> Unit,
    useFixedDarkColors: Boolean = true,
) {
    val chipSelectedColor = MaterialTheme.colorScheme.secondaryContainer
    val chipUnselectedColor = if (useFixedDarkColors) Color.Transparent
        else MaterialTheme.colorScheme.surfaceContainerLow
    val selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedLabelColor = if (useFixedDarkColors) Color.White
        else MaterialTheme.colorScheme.onSurfaceVariant
    val unselectedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val chipBorder: (Boolean) -> BorderStroke? = { selected ->
        if (useFixedDarkColors) {
            BorderStroke(1.dp, if (selected) Color.Transparent else Color.White)
        } else {
            BorderStroke(1.dp, if (selected) Color.Transparent else unselectedBorderColor)
        }
    }
    val sectionTitleColor = if (useFixedDarkColors) Color.White
        else MaterialTheme.colorScheme.onSurface
    val titleColor = if (useFixedDarkColors) Color.White
        else MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("更多设置", style = MaterialTheme.typography.titleMedium, color = titleColor)
            if (useFixedDarkColors) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close_outlined),
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close_outlined),
                        contentDescription = "关闭",
                        tint = titleColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Flip section
            Text(
                "画面翻转",
                style = MaterialTheme.typography.titleSmall,
                color = sectionTitleColor,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = flipHorizontal,
                    onClick = { onFlipHorizontalChange(!flipHorizontal) },
                    label = { Text("左右翻转") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedColor,
                        containerColor = chipUnselectedColor,
                        labelColor = unselectedLabelColor,
                        selectedLabelColor = selectedLabelColor,
                    ),
                    border = chipBorder(flipHorizontal),
                )
                FilterChip(
                    selected = flipVertical,
                    onClick = { onFlipVerticalChange(!flipVertical) },
                    label = { Text("上下翻转") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedColor,
                        containerColor = chipUnselectedColor,
                        labelColor = unselectedLabelColor,
                        selectedLabelColor = selectedLabelColor,
                    ),
                    border = chipBorder(flipVertical),
                )
            }
            Spacer(Modifier.height(20.dp))

            // Playback order
            Text(
                "播放顺序",
                style = MaterialTheme.typography.titleSmall,
                color = sectionTitleColor,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaybackOrder.entries.forEach { order ->
                    FilterChip(
                        selected = order == playbackOrder,
                        onClick = { onPlaybackOrderChange(order) },
                        label = { Text(order.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipSelectedColor,
                            containerColor = chipUnselectedColor,
                            labelColor = unselectedLabelColor,
                            selectedLabelColor = selectedLabelColor,
                        ),
                        border = chipBorder(order == playbackOrder),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Other settings
            Text(
                "其他设置",
                style = MaterialTheme.typography.titleSmall,
                color = sectionTitleColor,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = backgroundPlayback,
                    onClick = { onBackgroundPlaybackChange(!backgroundPlayback) },
                    label = { Text("后台播放") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedColor,
                        containerColor = chipUnselectedColor,
                        labelColor = unselectedLabelColor,
                        selectedLabelColor = selectedLabelColor,
                    ),
                    border = chipBorder(backgroundPlayback),
                )
                FilterChip(
                    selected = false,
                    onClick = { },
                    label = { Text("定时关闭") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipSelectedColor,
                        containerColor = chipUnselectedColor,
                        labelColor = unselectedLabelColor,
                        selectedLabelColor = selectedLabelColor,
                    ),
                    border = chipBorder(false),
                )
            }
            Spacer(Modifier.height(16.dp))
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
    window: android.view.Window?,
    audioManager: AudioManager,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isRewindingChange: (Boolean) -> Unit,
    isFastForwardingChange: (Boolean) -> Unit,
    brightnessHideJob: kotlinx.coroutines.Job?,
    onBrightnessHideJobChange: (kotlinx.coroutines.Job) -> Unit,
    volumeHideJob: kotlinx.coroutines.Job?,
    onVolumeHideJobChange: (kotlinx.coroutines.Job) -> Unit,
    seekHideJob: kotlinx.coroutines.Job?,
    onSeekHideJobChange: (kotlinx.coroutines.Job) -> Unit,
    isSeekingChange: (Boolean) -> Unit,
    seekPreviewPositionChange: (Long) -> Unit,
    isAdjustingBrightnessChange: (Boolean) -> Unit,
    isAdjustingVolumeChange: (Boolean) -> Unit,
    onVolumePercentChange: (Int) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    resetControlsTimer: () -> Unit,
    showControls: Boolean,
    isSwitchingEpisode: Boolean = false,
    playerIsPlaying: Boolean = false,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                val pv = android.view.LayoutInflater.from(ctx)
                    .inflate(R.layout.player_view_texture, null) as PlayerView
                pv.apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    player = exoPlayer
                    resizeMode = currentScaleMode.resizeMode
                }.also {
                    onPlayerViewRef(it)
                }
            },
            update = { playerView ->
                playerView.resizeMode = currentScaleMode.resizeMode
                playerView.scaleX = if (flipHorizontal) -1f else 1f
                playerView.scaleY = if (flipVertical) -1f else 1f
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
                Modifier.width(w.dp).height(h.dp)
            } ?: Modifier.fillMaxSize(),
        )
    }

    if (playerIsBuffering || isSwitchingEpisode) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ChrysanthemumLoading()
        }
    }

    if (isRewinding || isFastForwarding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Text(
                    text = if (isFastForwarding) "3.0x倍速前进" else "3.0x倍速后退",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier.fillMaxSize()
                .clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 48.dp, vertical = 24.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${percent}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier.fillMaxSize()
                .clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "${formatTime(seekPreviewPosition)} / ${formatTime(playerDuration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var singleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
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
                                (viewConfig.longPressTimeoutMillis - (System.currentTimeMillis() - downTime)).coerceAtLeast(1L)
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
                                if (kotlin.math.abs(dx) > viewConfig.touchSlop || kotlin.math.abs(dy) > viewConfig.touchSlop) {
                                    pastTouchSlop = true
                                    dragAxis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else 2
                                    if (dragAxis == 2) {
                                        brightnessHideJob?.cancel()
                                        isAdjustingBrightnessChange(true)
                                    } else {
                                        seekHideJob?.cancel()
                                        seekPreviewPositionChange(exoPlayer.currentPosition)
                                        isSeekingChange(true)
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
                                    val newPos = (exoPlayer.currentPosition + seekDelta).coerceIn(0, playerDuration)
                                    exoPlayer.seekTo(newPos)
                                    seekPreviewPositionChange(newPos)
                                }
                            }

                            lastPos = change.position
                        }

                        if (pastTouchSlop) {
                            if (dragAxis == 2) {
                                val job = scope.launch { delay(1500); isAdjustingBrightnessChange(false) }
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
                                (viewConfig.longPressTimeoutMillis - (System.currentTimeMillis() - downTime)).coerceAtLeast(1L)
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
                                if (kotlin.math.abs(dx) > viewConfig.touchSlop || kotlin.math.abs(dy) > viewConfig.touchSlop) {
                                    pastTouchSlop = true
                                    dragAxis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else 2
                                    if (dragAxis == 2) {
                                        volumeHideJob?.cancel()
                                        isAdjustingVolumeChange(true)
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        onVolumePercentChange((curVol * 100 / maxVol))
                                    } else {
                                        seekHideJob?.cancel()
                                        seekPreviewPositionChange(exoPlayer.currentPosition)
                                        isSeekingChange(true)
                                    }
                                }
                            }

                            if (pastTouchSlop) {
                                change.consume()
                                val delta = change.position - lastPos
                                if (dragAxis == 2) {
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val newVol = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - (delta.y / 300f * maxVol).toInt()).coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    onVolumePercentChange((newVol * 100 / maxVol))
                                } else if (dragAxis == 1 && playerDuration > 0) {
                                    val seekDelta = (delta.x / w * playerDuration).toLong()
                                    val newPos = (exoPlayer.currentPosition + seekDelta).coerceIn(0, playerDuration)
                                    exoPlayer.seekTo(newPos)
                                    seekPreviewPositionChange(newPos)
                                }
                            }

                            lastPos = change.position
                        }

                        if (pastTouchSlop) {
                            if (dragAxis == 2) {
                                val job = scope.launch { delay(1500); isAdjustingVolumeChange(false) }
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
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    val pv = android.view.LayoutInflater.from(ctx)
                        .inflate(R.layout.player_view_texture, null) as PlayerView
                    pv.apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        player = exoPlayer
                        resizeMode = currentScaleMode.resizeMode
                    }.also { onPlayerViewRef(it) }
                },
                update = { playerView ->
                    playerView.resizeMode = currentScaleMode.resizeMode
                    playerView.scaleX = if (flipHorizontal) -1f else 1f
                    playerView.scaleY = if (flipVertical) -1f else 1f
                },
                modifier = (currentScaleMode.aspectRatio?.let { ratio ->
                    val h = 240.dp
                    val w = (240f * ratio).dp
                    Modifier.width(w).height(h)
                } ?: Modifier.fillMaxSize()),
            )
        }

        if (playerIsBuffering || isSwitchingEpisode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ChrysanthemumLoading()
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
                FilledTonalIconButton(
                    onClick = {
                        if (playerDuration > 0 && playerCurrentPosition >= playerDuration - 500) {
                            exoPlayer.seekTo(0)
                        }
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        onInteraction()
                    },
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
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back_outlined),
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (onAudioPlay != null) {
                        IconButton(onClick = onAudioPlay) {
                            Icon(
                                painter = painterResource(R.drawable.headphones_outlined),
                                contentDescription = "音频播放",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (onCast != null) {
                        IconButton(onClick = onCast) {
                            Icon(
                                painter = painterResource(R.drawable.cast_outlined),
                                contentDescription = "投屏",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (onMoreSettings != null) {
                        IconButton(onClick = onMoreSettings) {
                            Icon(
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
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${formatTime(playerCurrentPosition)} / ${formatTime(playerDuration)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            PlayerSeekButton(
                                icon = R.drawable.replay_10_outlined,
                                text = "后退10s",
                                onClick = {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0L))
                                },
                            )
                            PlayerSeekButton(
                                icon = R.drawable.forward_10_outlined,
                                text = "前进10s",
                                onClick = {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(playerDuration))
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
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                painter = painterResource(
                                    if (playerIsPlaying) R.drawable.pause_filled
                                    else R.drawable.play_filled
                                ),
                                contentDescription = if (playerIsPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = onMuteToggle) {
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
                        IconButton(onClick = onFullscreen) {
                            Icon(
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
internal fun EpisodeOverlayContent(
    episodes: List<EpisodeInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pageSize = 100
    val totalEpisodes = episodes.size
    val categoryCount = (totalEpisodes + pageSize - 1) / pageSize
    val hasCategories = categoryCount > 1

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        pageCount = { categoryCount.coerceAtLeast(1) },
        initialPage = if (hasCategories) selectedIndex / pageSize else 0,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 32.dp),
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
                color = Color.White,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.close_outlined),
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (hasCategories) {
            androidx.compose.material3.PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                divider = {},
                edgePadding = 0.dp,
                contentColor = Color.White,
            ) {
                (0 until categoryCount).forEach { catIdx ->
                    val start = catIdx * pageSize + 1
                    val end = minOf((catIdx + 1) * pageSize, totalEpisodes)
                    androidx.compose.material3.Tab(
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
            androidx.compose.foundation.pager.HorizontalPager(
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
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = Color.White,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (realIdx == selectedIndex) MaterialTheme.colorScheme.outline else Color.White,
                                ),
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
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                labelColor = Color.White,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (index == selectedIndex) MaterialTheme.colorScheme.outline else Color.White,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
