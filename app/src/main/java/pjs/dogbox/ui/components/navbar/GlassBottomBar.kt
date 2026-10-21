package pjs.dogbox.ui.components.navbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pjs.dogbox.ui.components.navbar.liquid.InnerShadow
import pjs.dogbox.ui.components.navbar.liquid.InteractiveHighlight
import pjs.dogbox.ui.components.navbar.liquid.innerShadow
import pjs.dogbox.ui.components.navbar.liquid.lens
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** 底栏整体高度（含 4dp 内边距）。 */
private val BarHeight = 64.dp

/** 底栏内部可交互区域高度。 */
private val BarItemHeight = 56.dp

/** 底栏内边距。 */
private val BarInnerPadding = 4.dp

/** 单个入口的宽度。 */
private val BarItemWidth = 76.dp

/** 底栏距离屏幕底部的间距（不含系统栏）。 */
private val BarBottomMargin = 16.dp

/** 悬浮底栏占用的总高度，页面内容需要预留同等滚动空间。 */
internal val FloatingBarContentHeight = BarBottomMargin + BarHeight

private val BarIconSize = 24.dp

private val BarLabelSize = 11.sp

/**
 * 悬浮底栏。
 *
 * - [NavBarEffect.NONE]：纯色底栏 + 主色指示器。
 * - [NavBarEffect.FROSTED_GLASS]：毛玻璃，模糊身后的内容并叠加半透明表面。
 * - [NavBarEffect.LIQUID_GLASS]：液态玻璃，额外的色彩增强、边缘折射与随陀螺仪变化的高光。
 *
 * 支持在底栏上左右拖动切换入口，指示器跟随手指并带阻尼回弹。
 */
@Composable
fun GlassBottomBar(
    entries: List<NavBarEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    effect: NavBarEffect,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    val density = LocalDensity.current
    val densityScale = density.density
    val colors = MiuixTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val accent = colors.primary
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val itemCount = entries.size
    val safeIndex = selectedIndex.coerceIn(0, itemCount - 1)
    val barShape: Shape = RoundedCornerShape(percent = 50)
    val indicatorShape: Shape = RoundedCornerShape(percent = 50)
    val barWidth = BarItemWidth * itemCount + BarInnerPadding * 2
    val tabWidthPx = with(density) { BarItemWidth.toPx() }

    // 运行时着色器需要 Android 13+，否则降级为纯色底栏。
    val glassAvailable = backdrop != null && isRuntimeShaderSupported()

    val indicator = remember { Animatable(safeIndex.toFloat()) }
    val press = remember { Animatable(0f) }
    var dragValue by remember { mutableFloatStateOf(safeIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    val currentIndex by rememberUpdatedState(safeIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentDragging by rememberUpdatedState(dragging)

    LaunchedEffect(safeIndex) {
        if (!currentDragging) indicator.animateTo(safeIndex.toFloat(), spring(1f, 420f))
    }

    val surfaceHighlight = if (isDark) {
        Highlight.GlassStrokeMiddleDark
    } else {
        Highlight.GlassStrokeMiddleLight
    }
    val liquidHighlight = rememberTiltHighlight(LiquidHighlight, -45f)
    val pillHighlight = rememberTiltHighlight(LiquidHighlight, 90f)
    // 按下时跟随手指的高光反馈
    val interactiveHighlight = remember(scope) { InteractiveHighlight(scope) }

    val surfaceModifier: Modifier = when {
        effect == NavBarEffect.LIQUID_GLASS && backdrop != null && glassAvailable -> Modifier.glassSurface(
            effect = effect,
            backdrop = backdrop,
            shape = barShape,
            surfaceColor = colors.surface,
            tintAlpha = 0.24f,
            highlight = liquidHighlight,
        )

        effect == NavBarEffect.FROSTED_GLASS && backdrop != null && glassAvailable -> Modifier.glassSurface(
            effect = effect,
            backdrop = backdrop,
            shape = barShape,
            surfaceColor = colors.surface,
            tintAlpha = 0.52f,
            highlight = surfaceHighlight,
        )

        else -> Modifier.background(colors.surfaceContainer, barShape)
    }

    val indicatorModifier: Modifier = when {
        effect == NavBarEffect.LIQUID_GLASS && backdrop != null && glassAvailable -> Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { indicatorShape },
                effects = {
                    val progress = press.value
                    lens(
                        refractionHeight = 10f * densityScale * progress,
                        refractionAmount = 14f * densityScale * progress,
                        depthEffect = true,
                        chromaticAberration = 0.5f,
                    )
                },
                highlight = { pillHighlight.copy(alpha = 0.4f + press.value * 0.6f) },
                layerBlock = {
                    val scale = 1f + 0.06f * press.value
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect(colors.surface.copy(alpha = 0.28f))
                },
            )
            .innerShadow(indicatorShape) {
                InnerShadow(
                    radius = 8.dp * press.value,
                    color = Color.Black.copy(alpha = 0.18f),
                    alpha = press.value,
                )
            }

        effect == NavBarEffect.FROSTED_GLASS -> Modifier
            .background(colors.surface.copy(alpha = 0.55f), indicatorShape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), indicatorShape)

        else -> Modifier.background(accent.copy(alpha = 0.14f), indicatorShape)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = BarBottomMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(BarHeight)
                .then(if (effect != NavBarEffect.NONE && glassAvailable) interactiveHighlight.gestureModifier else Modifier)
                .pointerInput(itemCount, tabWidthPx) {
                    var dragStart = 0f
                    detectBarDrag(
                        onPress = {
                            scope.launch { press.animateTo(1f, spring(0.6f, 700f)) }
                        },
                        onRelease = {
                            scope.launch { press.animateTo(0f, spring(0.6f, 700f)) }
                        },
                        onDragStart = {
                            dragging = true
                            dragStart = indicator.value
                        },
                        onDrag = { delta ->
                            val raw = dragStart + delta / tabWidthPx
                            val clamped = raw.coerceIn(0f, (itemCount - 1).toFloat())
                            dragValue = clamped
                            scope.launch { indicator.snapTo(clamped) }
                        },
                        onDragEnd = { moved ->
                            dragging = false
                            if (moved) {
                                val target = dragValue.roundToInt().coerceIn(0, itemCount - 1)
                                scope.launch {
                                    indicator.animateTo(
                                        target.toFloat(),
                                        spring(1f, 320f)
                                    )
                                }
                                if (target != currentIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnSelect(target)
                                }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // 底板：毛玻璃 / 液态玻璃 / 纯色
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(surfaceModifier)
                    .then(if (effect != NavBarEffect.NONE && glassAvailable) interactiveHighlight.modifier else Modifier),
            )

            // 指示器：跟随选中项，可拖动
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(BarInnerPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .width(BarItemWidth)
                        .height(BarItemHeight)
                        .graphicsLayer { translationX = indicator.value * tabWidthPx }
                        .then(indicatorModifier),
                )
            }

            // 图标与文字
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(BarInnerPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEachIndexed { index, entry ->
                    GlassBottomBarItem(
                        selected = index == safeIndex,
                        label = entry.label,
                        icon = entry.icon,
                        selectedIcon = entry.selectedIcon,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(index)
                        },
                        modifier = Modifier.width(BarItemWidth),
                    )
                }
            }
        }
    }
}

/** 底栏入口：图标 + 文字，选中时使用主色与填充图标。 */
@Composable
private fun GlassBottomBarItem(
    selected: Boolean,
    label: String,
    icon: Int,
    selectedIcon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val tint = if (selected) colors.primary else colors.onSurface.copy(alpha = 0.55f)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .height(BarItemHeight)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interactionSource,
                role = Role.Tab,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(if (selected) selectedIcon else icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(BarIconSize),
        )
        MiuixText(
            text = label,
            color = tint,
            fontSize = BarLabelSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 在 Initial 阶段消费拖动事件，这样子节点的点击手势会随之取消，
 * 避免拖动结束时误触发点击。
 */
private suspend fun PointerInputScope.detectBarDrag(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (deltaX: Float) -> Unit,
    onDragEnd: (moved: Boolean) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPress()
        var total = 0f
        var moved = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed() || !change.pressed) break
            val delta = change.positionChange().x
            if (delta != 0f) total += delta
            if (!moved && abs(total) > slop) {
                moved = true
                onDragStart()
            }
            if (moved) {
                change.consume()
                onDrag(total)
            }
        }
        onDragEnd(moved)
        onRelease()
    }
}
