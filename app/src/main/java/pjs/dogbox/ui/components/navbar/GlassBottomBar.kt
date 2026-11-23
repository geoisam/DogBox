package pjs.dogbox.ui.components.navbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pjs.dogbox.ui.components.navbar.liquid.LiquidBarBottomMargin
import pjs.dogbox.ui.components.navbar.liquid.LiquidBarHeight
import pjs.dogbox.ui.components.navbar.liquid.LiquidGlassNavigationBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 底栏内部可交互区域高度。 */
private val BarHeight = 60.dp

/** 底栏与屏幕左右两侧的间距（miuix demo 标准 16dp）。 */
private val BarHorizontalMargin = 16.dp

/** 底栏距离屏幕底部的间距（不含系统栏）。 */
private val BarBottomMargin = 16.dp

/** 悬浮底栏占用的总高度，页面内容需要预留同等滚动空间。 */
internal val FloatingBarContentHeight = BarBottomMargin + BarHeight

/** 仿 iOS 液态玻璃底栏占用的总高度（栏体更高，内容留白随之加大）。 */
internal val LiquidBarContentHeight = LiquidBarBottomMargin + LiquidBarHeight

/** 按特效返回悬浮底栏占用的总高度；背板缺失时玻璃特效会降级为标准材质。 */
internal fun floatingBarContentHeight(effect: NavBarEffect, hasBackdrop: Boolean): Dp =
    if (effect == NavBarEffect.LIQUID_GLASS && hasBackdrop) LiquidBarContentHeight else FloatingBarContentHeight

/**
 * 悬浮底栏，三种材质均为「图标 + 文字」，对齐 miuix 官方示例的三档底栏样式：
 *
 * - [NavBarEffect.NONE]：标准材质。不采样背板，[GlassNavigationBar] 内部回退为
 *   不透明 surfaceContainer 纯色胶囊 + 默认 Float 悬浮阴影，选中指示器动画保留；
 * - [NavBarEffect.FROSTED_GLASS]：轻透磨砂。Pured Thin Glass 薄玻璃 + Common 玻璃样式，
 *   轻度模糊透出背后内容，描边 / 阴影用组件默认值；
 * - [NavBarEffect.LIQUID_GLASS]：仿 iOS 液态玻璃。移植 miuix 官方示例的
 *   IosLiquidGlassNavigationBar：vibrancy + 边缘折射透镜 + 可拖拽液态玻璃指示药丸。
 *
 * 配色完全交由组件默认值：动态取色开启时选中色为 primary，否则 onBackground。
 * 设备不支持运行时着色器或背板为 `null` 时，玻璃特效自动降级为标准材质。
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

    if (resolvedEffect == NavBarEffect.LIQUID_GLASS) {
        LiquidGlassNavigationBar(
            items = entries,
            selectedIndex = safeIndex,
            onItemClick = onSelectItem,
            backdrop = backdrop,
            modifier = modifier,
        )
        return
    }

    // 官方文档要求 item 实例稳定（badge 等状态跟随实例），用 remember 避免每次重组重建
    val items = remember(entries) {
        entries.map { entry ->
            GlassNavigationItem(
                icon = entry.icon,
                label = entry.label,
                contentDescription = entry.label,
            )
        }
    }

    GlassNavigationBar(
        items = items,
        selectedIndex = safeIndex,
        onSelect = onSelectItem,
        // 标准材质必须传 null 背板：否则组件仍会采样背景，材质层缺失时
        // 胶囊会变成一层透明玻璃膜。传 null 才会回退不透明纯色胶囊。
        backdrop = if (resolvedEffect == NavBarEffect.NONE) null else backdrop,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = BarHorizontalMargin,
                end = BarHorizontalMargin,
                bottom = BarBottomMargin,
            ),
        // 底栏位于 Scaffold 内容区内，系统栏间距与底部留白已由外部处理。
        defaultWindowInsetsPadding = false,
        // 轻透磨砂：Common glass，低饱和 + 轻柔模糊；标准材质样式不生效。
        style = if (resolvedEffect == NavBarEffect.FROSTED_GLASS) {
            GlassDefaults.style(isDark)
        } else {
            GlassDefaults.Style
        },
        stroke = if (resolvedEffect == NavBarEffect.FROSTED_GLASS) {
            GlassNavigationBarDefaults.stroke()
        } else {
            null
        },
        shadow = GlassShadows.Float,
        height = BarHeight,
        // 磨砂用薄玻璃；标准材质传 null，组件内部回退为 surfaceContainer 纯色胶囊。
        material = if (resolvedEffect == NavBarEffect.FROSTED_GLASS) {
            GlassMaterials.puredThinGlass(isDark)
        } else {
            null
        },
    )
}