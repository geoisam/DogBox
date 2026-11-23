package pjs.dogbox.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pjs.dogbox.data.repository.AppRepository
import pjs.dogbox.ui.components.topbar.AppGlassTopAppBar
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun SheetSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MiuixTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        MiuixText(
            text = title,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

/**
 * Miuix 子页面脚手架：统一玻璃返回栏 + 内容穿透顶栏。
 *
 * 按 miuix 官方示例（AdaptiveTopAppBar + pageScrollModifiers）的标准模式接线：
 * - 高级材质开启且设备支持运行时着色器时，录制内容背板供顶栏表面模糊与玻璃按钮采样，
 *   先铺一层不透明 surface 再录内容，避免无背景页面模糊成透明黑；
 * - 内置 [MiuixScrollBehavior]：大标题随内容滚动收起 / 展开，顶栏材质随滚动浮现；
 *   内容根已接好 nestedScroll 与 miuix 弹性 overscroll，页面滚动容器无需重复接线；
 * - [content] 收到的 [PaddingValues] 含顶栏高度，滚动容器应把 top 部分挪进
 *   contentPadding（或首元素 Spacer），让内容真正滚入顶栏之下被表面模糊采样。
 *
 * @param actions 顶栏右侧操作区，请使用 GlassIconButton
 */
@Composable
fun MiuixSubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    expandable: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val advanced = AppRepository.getAdvancedMaterial(LocalContext.current)
    // 玻璃采样依赖运行时着色器，Android 13 以下自动回退平材质。
    // 表面色必须在绘制 lambda 外捕获：rememberLayerBackdrop 的 lambda 非 @Composable。
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if (advanced && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    val scrollBehavior = MiuixScrollBehavior()

    MiuixScaffold(
        modifier = modifier,
        topBar = {
            AppGlassTopAppBar(
                title = title,
                onBack = onBack,
                actions = actions,
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
                expandable = expandable,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .overScrollVertical(
                    onOffsetChanged = { scrollBehavior.state.overscrollOffset = it },
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            content(padding)
        }
    }
}

/** Miuix 全屏加载中占位 */
@Composable
fun MiuixLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(size = 56.dp)
    }
}

/** Miuix 空数据占位 */
@Composable
fun MiuixEmptyText(text: String = "暂无数据", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MiuixText(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}
