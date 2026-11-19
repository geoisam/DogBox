package pjs.dogbox.ui.components.navbar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassStyles
import top.yukonga.miuix.kmp.material.GlassMaterials
import androidx.compose.ui.unit.dp
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
 * 悬浮底栏，三种材质均直接由 miuix 的 [GlassNavigationBar] 组件呈现：
 *
 * - [NavBarEffect.NONE]：标准材质（原有样式），传 `null` 背板，组件内部自动回退为
 *   不透明的 surfaceContainer 纯色胶囊；
 * - [NavBarEffect.FROSTED_GLASS]：轻透磨砂，Pured Thin Glass 薄玻璃 +
 *   Common 玻璃样式，轻度模糊透出背后内容；
 * - [NavBarEffect.LIQUID_GLASS]：柔光玻璃，iOS 风格：Bionic 玻璃样式 +
 *   PopupView 厚玻璃材质、更亮的描边和更高的阴影。
 *
 * 设备不支持运行时着色器时组件同样会自动回退为标准材质，无需外部判断。
 *
 * @param backdrop 页面内容的背板，`null` 时使用标准材质。
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
    val resolvedEffect = if (backdrop == null) NavBarEffect.NONE else effect

    val onSelectItem: (Int) -> Unit = { index ->
        if (index != safeIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onSelect(index)
    }

    val items = entries.mapIndexed { index, entry ->
        GlassNavigationItem(
            icon = if (index == safeIndex) entry.selectedIcon else entry.icon,
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
        onSelect = onSelectItem,
        backdrop = backdrop,
        modifier = modifier
            .padding(bottom = BarBottomMargin)
            .width(BarItemWidth * entries.size + BarInnerPadding * 2),
        // 底栏位于 Scaffold 内容区内，系统栏间距与底部留白已由外部处理。
        defaultWindowInsetsPadding = false,
        style = when (resolvedEffect) {
            // 轻透磨砂：Common glass，低饱和 + 轻柔模糊。
            NavBarEffect.FROSTED_GLASS -> GlassDefaults.style(isDark)
            // 柔光玻璃：Bionic glass，iOS 风格的高饱和、强折射。
            NavBarEffect.LIQUID_GLASS -> GlassStyles.BionicMediumRegular
            // 标准材质：默认样式（无背板时组件回退为纯色胶囊，样式不生效）。
            NavBarEffect.NONE -> GlassDefaults.Style
        },
        stroke = when (resolvedEffect) {
            NavBarEffect.FROSTED_GLASS -> GlassNavigationBarDefaults.stroke()
            // 柔光玻璃用更亮的描边强调玻璃边缘。
            NavBarEffect.LIQUID_GLASS -> GlassEdgeStrokes.forTheme(
                isDark = isDark,
                light = GlassEdgeStrokes.BigLight,
                dark = GlassEdgeStrokes.BigDark,
            )
            NavBarEffect.NONE -> null
        },
        shadow = when (resolvedEffect) {
            NavBarEffect.FROSTED_GLASS -> GlassShadows.Float
            NavBarEffect.LIQUID_GLASS -> GlassShadows.High
            NavBarEffect.NONE -> null
        },
        height = BarHeight,
        // 磨砂用薄玻璃，柔光玻璃用更厚、更亮的玻璃材质；标准材质传 null，
        // 组件内部回退为 surfaceContainer 纯色胶囊。
        material = when (resolvedEffect) {
            NavBarEffect.LIQUID_GLASS -> GlassMaterials.popupViewGlass(isDark)
            NavBarEffect.FROSTED_GLASS -> GlassMaterials.puredThinGlass(isDark)
            NavBarEffect.NONE -> null
        },
        indicatorColor = colors.primary.copy(alpha = indicatorAlpha),
        indicatorPressedColor = colors.primary.copy(alpha = indicatorPressedAlpha),
        selectedColor = colors.primary,
        unselectedColor = colors.onSurface.copy(alpha = labelAlpha),
    )
}
