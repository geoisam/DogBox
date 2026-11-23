package pjs.dogbox.ui.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pjs.dogbox.data.repository.AppRepository
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全应用统一的顶栏，按 miuix 官方示例（AdaptiveTopAppBar）的标准模式封装 [GlassTopAppBar]。
 *
 * 「高级材质」即顶栏表面模糊：开启且设备支持运行时着色器时，`progressiveBlur = true`，
 * 顶栏以渐进模糊实时采样身后内容（band 随滚动渐显），按钮渲染真玻璃胶囊；
 * 关闭时回退为 miuix 标准平材质顶栏：不透明 surface 背景、透明按钮填充、无描边阴影。
 *
 * 传入 [scrollBehavior] 并让页面滚动容器接上 `nestedScroll(scrollBehavior.nestedScrollConnection)`
 * 后，大标题随内容滚动收起 / 展开（miuix 规范）；顶栏材质也随内容是否滚到栏下自动浮现。
 * 主页 tab 等紧凑场景传 `expandable = false` 保持小标题常显。
 *
 * 背板由页面自行录制（`rememberLayerBackdrop { drawRect(surface); drawContent() }` +
 * 内容根 `Modifier.layerBackdrop(backdrop)`），组件只负责按材质开关决定是否消费。
 *
 * @param title 顶栏标题（展开时为大标题，收起后为居中小标题）
 * @param onBack 返回回调，`null` 时不渲染返回键（主页 tab 场景）
 * @param actions 右侧操作区，请使用 [GlassIconButton] 保持玻璃按钮一致
 * @param backdrop 页面内容背板；仅在高级材质开启时被消费
 * @param scrollBehavior 页面滚动行为，驱动大标题折叠与材质浮现
 * @param expandable 是否允许大标题展开，`false` 时小标题常显
 */
@Composable
fun AppGlassTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backdrop: Backdrop? = null,
    scrollBehavior: ScrollBehavior? = null,
    expandable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val advanced = AppRepository.getAdvancedMaterial(LocalContext.current) &&
        isRuntimeShaderSupported() && backdrop != null
    val surface = MiuixTheme.colorScheme.surface

    GlassTopAppBar(
        title = title,
        // 平材质时整栏铺不透明 surface（含状态栏区域），与 miuix 标准 TopAppBar 观感一致；
        // 高级材质时背景交给渐进模糊 band，随滚动渐显。
        modifier = if (advanced) modifier else modifier.background(surface),
        expandable = expandable,
        progressiveBlur = advanced,
        backdrop = if (advanced) backdrop else null,
        scrollBehavior = scrollBehavior,
        bandBrush = if (advanced) {
            GlassTopAppBarDefaults.bandBrush(surface)
        } else {
            SolidColor(surface)
        },
        // 平材质不需要压暗内容的渐变垂带，避免栏底多出一截不透明“台阶”
        bandOverhang = if (advanced) GlassTopAppBarDefaults.BandOverhang else 0.dp,
        fill = if (advanced) GlassTopAppBarDefaults.buttonFill() else Color.Transparent,
        stroke = if (advanced) GlassTopAppBarDefaults.buttonStroke() else null,
        buttonShadow = if (advanced) GlassShadows.Regular else null,
        navigationIcon = if (onBack != null) {
            {
                GlassIconButton(onClick = onBack) {
                    MiuixIcon(
                        imageVector = MiuixIcons.Glass.ChevronBackward,
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        } else {
            {}
        },
        actions = actions,
    )
}