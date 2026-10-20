package pjs.dogbox.ui.screen.discover

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import pjs.dogbox.R
import pjs.dogbox.data.SharedData
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private enum class ScrollDirection {
    RIGHT_TO_LEFT,
    LEFT_TO_RIGHT,
    BOTTOM_TO_TOP,
    TOP_TO_BOTTOM
}

private enum class HorizontalPosition {
    LEFT, CENTER, RIGHT
}

private enum class VerticalPosition {
    TOP, CENTER, BOTTOM
}

private fun colorToHex(color: Color): String =
    String.format("#%08X", color.toArgb())

private fun colorFromHex(hex: String): Color? = runCatching {
    var clean = hex.trim().removePrefix("#")
    // 参考 lyricon parseHexColor：6 位自动补 FF，8 位保留 alpha
    if (clean.length == 6) clean = "FF$clean"
    require(clean.length == 8) { "Invalid color: $hex" }
    Color(clean.toULong(16).toLong())
}.getOrNull()

@Composable
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember(currentColor) { mutableStateOf(currentColor) }
    var hexInput by remember(currentColor) { mutableStateOf(colorToHex(currentColor).removePrefix("#")) }

    OverlayDialog(
        show = true,
        title = "选择颜色",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ColorPalette(
                color = selectedColor,
                onColorChanged = { newColor ->
                    selectedColor = newColor
                    hexInput = colorToHex(newColor).removePrefix("#")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            MiuixText(
                text = "HEX:#",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(4.dp))
            TextField(
                value = hexInput,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isLetterOrDigit() }.take(8)
                    hexInput = filtered
                    colorFromHex("#$filtered")?.let { selectedColor = it }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { onColorSelected(selectedColor) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 参考 lyricon ColorBox.drawCheckerboard：透明色预览必须垫棋盘格，否则透明看起来像黑色。
 */
@Composable
private fun TransparentColorPreview(
    color: Color,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
    ) {
        val checker = 4.dp.toPx()
        drawRect(color = Color(0xFFE0E0E0))
        val sizePx = size.toPx()
        val columns = (sizePx / checker).toInt() + 1
        val rows = (sizePx / checker).toInt() + 1
        for (i in 0 until columns) {
            for (j in 0 until rows) {
                if ((i + j) % 2 != 0) {
                    drawRect(
                        color = Color(0xFFB0B0B0),
                        topLeft = androidx.compose.ui.geometry.Offset(i * checker, j * checker),
                        size = androidx.compose.ui.geometry.Size(checker, checker),
                    )
                }
            }
        }
        drawCircle(color = color)
    }
}

@Composable
private fun MarqueeText(
    text: String,
    direction: ScrollDirection,
    speedSeconds: Int,
    fontSizeSp: Int,
    letterSpacingSp: Int,
    lineHeightSp: Int,
    horizontalAlignment: HorizontalPosition,
    verticalAlignment: VerticalPosition,
    isBold: Boolean,
    isItalic: Boolean,
    isUnderline: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return

    var textWidth by remember { mutableStateOf(0f) }
    var textHeight by remember { mutableStateOf(0f) }
    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }

    val isDynamic = speedSeconds > 0
    val actualSpeed = if (isDynamic) speedSeconds * 1000 else 6000
    // lineHeightSp = 0 时使用系统默认行高，避免 lineHeight = fontSize 导致裁剪；
    // 仅在用户显式设置行高时才覆盖（参考 lyricon 文本 margins/paddings 逻辑：默认不干预）。
    val resolvedLineHeight =
        if (lineHeightSp > 0) (fontSizeSp + lineHeightSp).sp else androidx.compose.ui.unit.TextUnit.Unspecified
    val textStyle = TextStyle(
        color = textColor,
        fontSize = fontSizeSp.sp,
        letterSpacing = letterSpacingSp.sp,
        lineHeight = resolvedLineHeight,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None
    )

    if (isDynamic) {
        val infiniteTransition = rememberInfiniteTransition()
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = actualSpeed,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            )
        )

        Box(
            modifier = modifier
                .onSizeChanged {
                    containerWidth = it.width.toFloat()
                    containerHeight = it.height.toFloat()
                }
        ) {
            val isHorizontal = direction == ScrollDirection.RIGHT_TO_LEFT
                    || direction == ScrollDirection.LEFT_TO_RIGHT

            val horAlignmentOffset = if (!isHorizontal) when (horizontalAlignment) {
                HorizontalPosition.LEFT -> 0f
                HorizontalPosition.CENTER -> ((containerWidth - textWidth) / 2f).coerceAtLeast(0f)
                HorizontalPosition.RIGHT -> (containerWidth - textWidth).coerceAtLeast(0f)
            } else 0f
            val verAlignmentOffset = if (isHorizontal) when (verticalAlignment) {
                VerticalPosition.TOP -> 0f
                VerticalPosition.CENTER -> ((containerHeight - textHeight) / 2f).coerceAtLeast(0f)
                VerticalPosition.BOTTOM -> (containerHeight - textHeight).coerceAtLeast(0f)
            } else 0f

            if (isHorizontal) {
                val margin = containerWidth * 0.15f
                val totalDistance = containerWidth + textWidth + 2 * margin
                val offsetX = when (direction) {
                    ScrollDirection.RIGHT_TO_LEFT ->
                        (containerWidth + margin) - totalDistance * progress

                    ScrollDirection.LEFT_TO_RIGHT ->
                        (-textWidth - margin) + totalDistance * progress

                    else -> 0f
                }

                MiuixText(
                    text = text,
                    style = textStyle,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    onTextLayout = {
                        textWidth = it.size.width.toFloat()
                        textHeight = it.size.height.toFloat()
                    },
                    modifier = Modifier.graphicsLayer {
                        translationX = offsetX
                        translationY = verAlignmentOffset
                    }
                )
            } else {
                val margin = containerHeight * 0.15f
                val totalDistance = containerHeight + textHeight + 2 * margin
                val offsetY = when (direction) {
                    ScrollDirection.BOTTOM_TO_TOP ->
                        (containerHeight + margin) - totalDistance * progress

                    ScrollDirection.TOP_TO_BOTTOM ->
                        (-textHeight - margin) + totalDistance * progress

                    else -> 0f
                }

                MiuixText(
                    text = text,
                    style = textStyle,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    onTextLayout = {
                        textWidth = it.size.width.toFloat()
                        textHeight = it.size.height.toFloat()
                    },
                    modifier = Modifier.graphicsLayer {
                        translationX = horAlignmentOffset
                        translationY = offsetY
                    }
                )
            }
        }
    } else {
        val contentAlignment = when (horizontalAlignment) {
            HorizontalPosition.LEFT -> when (verticalAlignment) {
                VerticalPosition.TOP -> Alignment.TopStart
                VerticalPosition.CENTER -> Alignment.CenterStart
                VerticalPosition.BOTTOM -> Alignment.BottomStart
            }

            HorizontalPosition.CENTER -> when (verticalAlignment) {
                VerticalPosition.TOP -> Alignment.TopCenter
                VerticalPosition.CENTER -> Alignment.Center
                VerticalPosition.BOTTOM -> Alignment.BottomCenter
            }

            HorizontalPosition.RIGHT -> when (verticalAlignment) {
                VerticalPosition.TOP -> Alignment.TopEnd
                VerticalPosition.CENTER -> Alignment.CenterEnd
                VerticalPosition.BOTTOM -> Alignment.BottomEnd
            }
        }
        val textAlign = when (horizontalAlignment) {
            HorizontalPosition.LEFT -> TextAlign.Start
            HorizontalPosition.CENTER -> TextAlign.Center
            HorizontalPosition.RIGHT -> TextAlign.End
        }
        Box(
            modifier = modifier.onSizeChanged {
                containerWidth = it.width.toFloat()
                containerHeight = it.height.toFloat()
            },
            contentAlignment = contentAlignment
        ) {
            MiuixText(
                text = text,
                style = textStyle,
                textAlign = textAlign,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    range: IntRange,
    increment: Int,
    zeroLabel: String? = null,
    plain: Boolean = false,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIncrement = increment.coerceAtLeast(1)
    // steps = 区间数 - 1（Miuix/Compose Slider 语义），不足一档时为 0（连续滑动）
    val steps = if (plain) 0 else ((range.last - range.first) / safeIncrement - 1).coerceAtLeast(0)
    val keyPointCount = (range.last - range.first) / safeIncrement + 1
    val keyPoints = remember(range.first, range.last, safeIncrement) {
        List(keyPointCount) { (range.first + it * safeIncrement).toFloat() }
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixText(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            MiuixText(
                text = if (value == 0 && zeroLabel != null) zeroLabel else "$value",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                // 以 range.first 为基准吸附，避免 range 不从 0 起始时错位
                val snapped =
                    (((it - range.first) / safeIncrement).roundToInt() * safeIncrement + range.first)
                        .coerceIn(range.first, range.last)
                onValueChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            showKeyPoints = !plain,
            keyPoints = if (plain) emptyList() else keyPoints,
            magnetThreshold = if (plain) 0f else 0.03f,
            hapticEffect = if (plain) SliderDefaults.SliderHapticEffect.None
            else SliderDefaults.SliderHapticEffect.Step,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StyleToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) MiuixTheme.colorScheme.primary
    else MiuixTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (selected) MiuixTheme.colorScheme.onPrimary
    else MiuixTheme.colorScheme.onSurfaceSecondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        MiuixText(
            text = label,
            color = contentColor,
            style = MiuixTheme.textStyles.body1
        )
    }
}

@Composable
private fun SettingColorRow(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixText(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        TransparentColorPreview(color = color, size = 28.dp)
    }
}

@Composable
private fun FullScreenPlayback(
    text: String,
    direction: ScrollDirection,
    speedSeconds: Int,
    fontSizeSp: Int,
    letterSpacingSp: Int,
    lineHeightSp: Int,
    horizontalAlignment: HorizontalPosition,
    verticalAlignment: VerticalPosition,
    isBold: Boolean,
    isItalic: Boolean,
    isUnderline: Boolean,
    bgColor: Color,
    textColor: Color,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val dialogView = LocalView.current

        DisposableEffect(bgColor) {
            val activity = view.context as? Activity
            val activityWindow = activity?.window

            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // 参考 lyricon Theme 的状态栏写法：优先走 WindowInsetsControllerCompat，
            // 废弃的 systemUiVisibility 仅作为低版本兜底，避免与 edge-to-edge 冲突。
            val rootView = dialogView.rootView
            val insetsController =
                activityWindow?.let { WindowInsetsControllerCompat(it, rootView) }
            // 全屏播放时隐藏系统栏，滑动可短暂唤出
            insetsController?.let {
                it.hide(WindowInsetsCompat.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            if (Build.VERSION.SDK_INT >= 30) {
                rootView.windowInsetsController?.let { controller ->
                    controller.hide(
                        android.view.WindowInsets.Type.statusBars()
                                or android.view.WindowInsets.Type.navigationBars()
                    )
                    controller.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                rootView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
            }
            // Configure cutout mode on the dialog window
            (rootView.layoutParams as? WindowManager.LayoutParams)?.let { params ->
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            // Configure dialog window flags for fullscreen
            var dialogWin: android.view.Window? = null
            var v: android.view.View? = dialogView
            while (v != null) {
                if (v is DialogWindowProvider) {
                    dialogWin = v.window
                    break
                }
                v = v.parent as? android.view.View
            }
            dialogWin?.apply {
                setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
                setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
            }
            // Configure activity window for system bar colors
            if (activityWindow != null) {
                activityWindow.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                activityWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                activityWindow.statusBarColor = bgColor.toArgb()
                activityWindow.navigationBarColor = bgColor.toArgb()
                WindowInsetsControllerCompat(activityWindow, activityWindow.decorView)
                    .isAppearanceLightStatusBars = bgColor.luminance() > 0.5f
            }

            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (activityWindow != null) {
                    activityWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                    activityWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .clickable { onDismiss() },
            contentAlignment = Alignment.CenterStart
        ) {
            MarqueeText(
                text = text,
                direction = direction,
                speedSeconds = speedSeconds,
                fontSizeSp = fontSizeSp,
                letterSpacingSp = letterSpacingSp,
                lineHeightSp = lineHeightSp,
                horizontalAlignment = horizontalAlignment,
                verticalAlignment = verticalAlignment,
                isBold = isBold,
                isItalic = isItalic,
                isUnderline = isUnderline,
                textColor = textColor,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ScrollingTextScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val savedConfig = remember { SharedData.getScrollingTextConfig(context) }

    var text by remember { mutableStateOf(savedConfig.text) }
    var direction by remember { mutableStateOf(ScrollDirection.entries.getOrElse(savedConfig.direction) { ScrollDirection.RIGHT_TO_LEFT }) }
    var speedSeconds by remember { mutableIntStateOf(savedConfig.speedSeconds) }
    var fontSizeSp by remember { mutableIntStateOf(savedConfig.fontSizeSp) }
    var letterSpacingSp by remember { mutableIntStateOf(savedConfig.letterSpacingSp) }
    var lineHeightSp by remember { mutableIntStateOf(savedConfig.lineHeightSp) }
    var isBold by remember { mutableStateOf(savedConfig.isBold) }
    var isItalic by remember { mutableStateOf(savedConfig.isItalic) }
    var isUnderline by remember { mutableStateOf(savedConfig.isUnderline) }
    var horizontalAlignment by remember {
        mutableStateOf(
            HorizontalPosition.entries.getOrElse(
                savedConfig.horizontalAlignment
            ) { HorizontalPosition.CENTER })
    }
    var verticalAlignment by remember {
        mutableStateOf(
            VerticalPosition.entries.getOrElse(
                savedConfig.verticalAlignment
            ) { VerticalPosition.CENTER })
    }
    var bgColor by remember { mutableStateOf(Color(savedConfig.bgColor)) }
    var textColor by remember { mutableStateOf(Color(savedConfig.textColor)) }
    var showFullScreenPlayback by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }
    var showTextColorPicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    fun resetDefaults() {
        text = "我爱你"
        direction = ScrollDirection.RIGHT_TO_LEFT
        speedSeconds = 10
        fontSizeSp = 250
        letterSpacingSp = 0
        lineHeightSp = 0
        isBold = true
        isItalic = false
        isUnderline = false
        horizontalAlignment = HorizontalPosition.CENTER
        verticalAlignment = VerticalPosition.CENTER
        bgColor = Color(0xFF000000)
        textColor = Color(0xFFFFFFFF)
    }

    if (showBgColorPicker) {
        ColorPickerDialog(
            currentColor = bgColor,
            onColorSelected = { bgColor = it; showBgColorPicker = false },
            onDismiss = { showBgColorPicker = false }
        )
    }

    if (showTextColorPicker) {
        ColorPickerDialog(
            currentColor = textColor,
            onColorSelected = { textColor = it; showTextColorPicker = false },
            onDismiss = { showTextColorPicker = false }
        )
    }

    if (showFullScreenPlayback && text.isNotBlank()) {
        FullScreenPlayback(
            text = text,
            direction = direction,
            speedSeconds = speedSeconds,
            fontSizeSp = fontSizeSp,
            letterSpacingSp = letterSpacingSp,
            lineHeightSp = lineHeightSp,
            horizontalAlignment = horizontalAlignment,
            verticalAlignment = verticalAlignment,
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            bgColor = bgColor,
            textColor = textColor,
            onDismiss = { showFullScreenPlayback = false }
        )
    }

    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "滚动字幕",
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            resetDefaults()
                        },
                    ) {
                        MiuixText("重置默认值")
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            SharedData.saveScrollingTextConfig(
                                context,
                                SharedData.ScrollingTextConfig(
                                    text = text,
                                    direction = direction.ordinal,
                                    speedSeconds = speedSeconds,
                                    fontSizeSp = fontSizeSp,
                                    letterSpacingSp = letterSpacingSp,
                                    lineHeightSp = lineHeightSp,
                                    horizontalAlignment = horizontalAlignment.ordinal,
                                    verticalAlignment = verticalAlignment.ordinal,
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    isUnderline = isUnderline,
                                    bgColor = bgColor.toArgb(),
                                    textColor = textColor.toArgb(),
                                )
                            )
                            showFullScreenPlayback = true
                        },
                        enabled = text.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        MiuixText("全屏播放")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = "输入文字内容",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        maxLines = 6,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SliderSetting(
                        label = "播放速度（秒）",
                        value = speedSeconds,
                        range = 0..30,
                        increment = 1,
                        zeroLabel = "0（静态）",
                        plain = true,
                        onValueChange = { speedSeconds = it }
                    )
                    SliderSetting(
                        label = "文字大小（sp）",
                        value = fontSizeSp,
                        range = 10..1000,
                        increment = 10,
                        plain = true,
                        onValueChange = { fontSizeSp = it }
                    )
                    SliderSetting(
                        label = "文字间距（sp）",
                        value = letterSpacingSp,
                        range = 0..100,
                        increment = 5,
                        onValueChange = { letterSpacingSp = it }
                    )
                    SliderSetting(
                        label = "文字行高（sp）",
                        value = lineHeightSp,
                        range = 0..100,
                        increment = 5,
                        onValueChange = { lineHeightSp = it }
                    )

                    Spacer(Modifier.height(4.dp))

                    MiuixText(
                        "文字样式",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyleToggle(
                            label = "加粗",
                            selected = isBold,
                            onClick = { isBold = !isBold },
                            modifier = Modifier.weight(1f)
                        )
                        StyleToggle(
                            label = "斜体",
                            selected = isItalic,
                            onClick = { isItalic = !isItalic },
                            modifier = Modifier.weight(1f)
                        )
                        StyleToggle(
                            label = "下划线",
                            selected = isUnderline,
                            onClick = { isUnderline = !isUnderline },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OverlayDropdownPreference(
                    title = "滚动方向",
                    items = listOf("从右到左", "从左到右", "从下到上", "从上到下"),
                    selectedIndex = direction.ordinal,
                    onSelectedIndexChange = { direction = ScrollDirection.entries[it] },
                )
                OverlayDropdownPreference(
                    title = "水平位置",
                    items = listOf("居左", "居中", "居右"),
                    selectedIndex = horizontalAlignment.ordinal,
                    onSelectedIndexChange = {
                        horizontalAlignment = HorizontalPosition.entries[it]
                    },
                )
                OverlayDropdownPreference(
                    title = "垂直位置",
                    items = listOf("居上", "居中", "居下"),
                    selectedIndex = verticalAlignment.ordinal,
                    onSelectedIndexChange = { verticalAlignment = VerticalPosition.entries[it] },
                )
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    SettingColorRow(
                        label = "背景颜色",
                        color = bgColor,
                        onClick = { showBgColorPicker = true }
                    )
                    SettingColorRow(
                        label = "文字颜色",
                        color = textColor,
                        onClick = { showTextColorPicker = true }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
