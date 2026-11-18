package pjs.dogbox.ui.components.navbar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassStyles
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 底栏内部可交互区域高度。 */
private val BarHeight = 60.dp

/** 单个入口的宽度。 */
private val BarItemWidth = 76.dp

/** 底栏内边距。 */
private val BarInnerPadding = 4.dp

/** 底栏距离屏幕底部的间距（不含系统栏）。 */
private val BarBottomMargin = 16.dp

/** 悬浮底栏占用的总高度，页面内容需要预留同等滚动空间。 */
internal val FloatingBarContentHeight = BarBottomMargin + BarHeight

/**
 * 悬浮底栏，基于 miuix-glass 的 [GlassNavigationBar]。
 *
 * - [NavBarEffect.NONE]：默认材质，原生图标 + 文字的纯色胶囊，不采样背景。
 * - [NavBarEffect.FROSTED_GLASS]：轻透磨砂，miuix-glass 的 Pured Thin Glass：
 *   轻度模糊 + 薄薄的柔光层，透出背后内容。
 * - [NavBarEffect.LIQUID_GLASS]：柔光玻璃，iOS 风格：更重的模糊与柔光层，
 *   配合 Bionic 玻璃样式与更亮的描边。
 *
 * 指示器跟随手指拖动、回弹与按压反馈均由库实现；此处只负责图标、配色与触感反馈。
 * 设备不支持运行时着色器或没有背板时，玻璃效果会自动降级为默认材质。
 *
 * @param backdrop 页面内容的背板，`null` 时使用纯色底栏。
 */
@Composable
fun GlassBottomBar(
    entries: List<NavBarEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    effect: NavBarEffect,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    val colors = MiuixTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val haptic = LocalHapticFeedback.current

    val safeIndex = selectedIndex.coerceIn(0, entries.lastIndex)
    val glassAvailable = backdrop != null && isRuntimeShaderSupported()
    val resolvedEffect = if (glassAvailable) effect else NavBarEffect.NONE

    val items = entries.mapIndexed { index, entry ->
        GlassNavigationItem(
            icon = ImageVector.vectorResource(
                if (index == safeIndex) entry.selectedIcon else entry.icon
            ),
            label = entry.label,
            contentDescription = entry.label,
        )
    }

    val labelAlpha = if (isDark) 0.62f else 0.55f
    val indicatorAlpha = if (isDark) 0.22f else 0.14f
    val indicatorPressedAlpha = if (isDark) 0.34f else 0.22f

    GlassNavigationBar(
        items = items,
        selectedIndex = safeIndex,
        onSelect = { index ->
            if (index != safeIndex) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onSelect(index)
        },
        backdrop = if (glassAvailable) backdrop else null,
        modifier = modifier
            .padding(bottom = BarBottomMargin)
            .width(BarItemWidth * entries.size + BarInnerPadding * 2),
        // 底栏位于 Scaffold 内容区内，系统栏间距与底部留白已由外部处理。
        defaultWindowInsetsPadding = false,
        style = when (resolvedEffect) {
            // 轻透磨砂：Common glass，低饱和 + 轻柔模糊。
            NavBarEffect.FROSTED_GLASS -> GlassDefaults.style(isDark)
            // 柔光玻璃：Bionic glass，iOS 风格的高饱和、强折射。
            NavBarEffect.LIQUID_GLASS -> GlassStyles.BionicMediumNormal
            NavBarEffect.NONE -> GlassDefaults.Style
        },
        // 默认材质不加玻璃描边，避免纯色底栏出现"特殊效果"。
        stroke = when (resolvedEffect) {
            NavBarEffect.NONE -> null
            NavBarEffect.FROSTED_GLASS -> GlassNavigationBarDefaults.stroke()
            // 柔光玻璃用更亮的描边强调玻璃边缘。
            NavBarEffect.LIQUID_GLASS -> GlassEdgeStrokes.forTheme(
                isDark = isDark,
                light = GlassEdgeStrokes.BigLight,
                dark = GlassEdgeStrokes.BigDark,
            )
        },
        shadow = GlassShadows.Float,
        height = BarHeight,
        // 磨砂用薄玻璃，柔光玻璃用更厚、更亮的玻璃材质。
        material = when (resolvedEffect) {
            NavBarEffect.LIQUID_GLASS -> GlassMaterials.popupViewGlass(isDark)
            else -> GlassMaterials.puredThinGlass(isDark)
        },
        indicatorColor = colors.primary.copy(alpha = indicatorAlpha),
        indicatorPressedColor = colors.primary.copy(alpha = indicatorPressedAlpha),
        selectedColor = colors.primary,
        unselectedColor = colors.onSurface.copy(alpha = labelAlpha),
    )
}
