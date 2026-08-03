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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kotlin.math.roundToInt
import pjs.dogbox.R
import pjs.dogbox.ui.theme.AppColorThemes
import pjs.dogbox.util.SharedData

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
    String.format("#%06X", 0xFFFFFF and color.toArgb())

private fun colorFromHex(hex: Int): Color = Color(
    red = ((hex shr 16) and 0xFF) / 255f,
    green = ((hex shr 8) and 0xFF) / 255f,
    blue = (hex and 0xFF) / 255f
)

@Composable
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = remember {
        listOf(Color.Black, Color.White) + AppColorThemes.map { it.primary }
    }
    var selectedColor by remember(currentColor) { mutableStateOf(currentColor) }
    var hexInput by remember(currentColor) { mutableStateOf(colorToHex(currentColor)) }

    fun select(color: Color) {
        selectedColor = color
        hexInput = colorToHex(color)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                presetColors.chunked(7).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowColors.forEach { color ->
                            val isSelected = selectedColor.toArgb() == color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Gray.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { select(color) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { newValue ->
                        hexInput = newValue
                        val clean = newValue.trimStart('#').take(6)
                        if (clean.length == 6 && clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                            try {
                                selectedColor = colorFromHex(clean.toInt(16))
                            } catch (_: NumberFormatException) {
                            }
                        }
                    },
                    label = { Text("颜色代码 (#RRGGBB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
    val textStyle = TextStyle(
        color = textColor,
        fontSize = fontSizeSp.sp,
        letterSpacing = letterSpacingSp.sp,
        lineHeight = (fontSizeSp + lineHeightSp).sp,
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
                }

                Text(
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
                }

                Text(
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
            Text(
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
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = (range.last - range.first) / increment - 1
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (value == 0 && zeroLabel != null) zeroLabel else "$value",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val snapped = ((it / increment).roundToInt() * increment).coerceIn(range.first, range.last)
                onValueChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
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
    val containerColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun <T> SettingDropdownRow(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.arrow_drop_down_outlined),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (option, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
        )
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

        DisposableEffect(Unit) {
            val activity = view.context as? Activity
            val activityWindow = activity?.window

            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // Configure dialog for immersive fullscreen
            val rootView = dialogView.rootView
            rootView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
            if (Build.VERSION.SDK_INT >= 30) {
                rootView.windowInsetsController?.let { controller ->
                    controller.hide(
                        android.view.WindowInsets.Type.statusBars()
                                or android.view.WindowInsets.Type.navigationBars()
                    )
                    controller.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
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
                activityWindow.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }

            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (activityWindow != null) {
                    activityWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                    activityWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                    activityWindow.decorView.systemUiVisibility = (
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            )
                }
                if (dialogView.rootView !== activityWindow?.decorView) {
                    dialogView.rootView.systemUiVisibility =
                        android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var horizontalAlignment by remember { mutableStateOf(HorizontalPosition.entries.getOrElse(savedConfig.horizontalAlignment) { HorizontalPosition.CENTER }) }
    var verticalAlignment by remember { mutableStateOf(VerticalPosition.entries.getOrElse(savedConfig.verticalAlignment) { VerticalPosition.CENTER }) }
    var bgColor by remember { mutableStateOf(Color(savedConfig.bgColor)) }
    var textColor by remember { mutableStateOf(Color(savedConfig.textColor)) }
    var showFullScreenPlayback by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }
    var showTextColorPicker by remember { mutableStateOf(false) }

    val directionLabel = when (direction) {
        ScrollDirection.RIGHT_TO_LEFT -> "从右到左"
        ScrollDirection.LEFT_TO_RIGHT -> "从左到右"
        ScrollDirection.BOTTOM_TO_TOP -> "从下到上"
        ScrollDirection.TOP_TO_BOTTOM -> "从上到下"
    }
    val horizontalLabel = when (horizontalAlignment) {
        HorizontalPosition.LEFT -> "居左"
        HorizontalPosition.CENTER -> "居中"
        HorizontalPosition.RIGHT -> "居右"
    }
    val verticalLabel = when (verticalAlignment) {
        VerticalPosition.TOP -> "居上"
        VerticalPosition.CENTER -> "居中"
        VerticalPosition.BOTTOM -> "居下"
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = { Text("滚动字幕", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            navigationIcon = {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_outlined),
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("输入文字内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(min = 120.dp),
                    maxLines = 100,
                )
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SliderSetting(
                        label = "播放速度（秒）",
                        value = speedSeconds,
                        range = 0..30,
                        increment = 1,
                        zeroLabel = "0（静态）",
                        onValueChange = { speedSeconds = it }
                    )
                    SliderSetting(
                        label = "文字大小（sp）",
                        value = fontSizeSp,
                        range = 10..500,
                        increment = 10,
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

                    Text(
                        "文字样式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column {
                    SettingDropdownRow(
                        label = "滚动方向",
                        value = directionLabel,
                        options = listOf(
                            ScrollDirection.RIGHT_TO_LEFT to "从右到左",
                            ScrollDirection.LEFT_TO_RIGHT to "从左到右",
                            ScrollDirection.BOTTOM_TO_TOP to "从下到上",
                            ScrollDirection.TOP_TO_BOTTOM to "从上到下"
                        ),
                        onSelected = { direction = it }
                    )
                    SettingDropdownRow(
                        label = "水平位置",
                        value = horizontalLabel,
                        options = listOf(
                            HorizontalPosition.LEFT to "居左",
                            HorizontalPosition.CENTER to "居中",
                            HorizontalPosition.RIGHT to "居右"
                        ),
                        onSelected = { horizontalAlignment = it }
                    )
                    SettingDropdownRow(
                        label = "垂直位置",
                        value = verticalLabel,
                        options = listOf(
                            VerticalPosition.TOP to "居上",
                            VerticalPosition.CENTER to "居中",
                            VerticalPosition.BOTTOM to "居下"
                        ),
                        onSelected = { verticalAlignment = it }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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

        // ── Bottom Action Bar ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        resetDefaults()
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("重置默认值")
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
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("全屏播放")
                }
            }
        }
    }
}
