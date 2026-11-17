package pjs.dogbox.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pjs.dogbox.R
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon

/**
 * 全应用统一的“回到顶部”悬浮按钮。
 *
 * 样式固定为：主色圆形底、onPrimary 图标、右下角 24dp 边距，
 * 避免各页面各自设置颜色/间距导致观感不一致。
 *
 * @param extraBottomPadding 额外的底部间距，用于避开悬浮导航栏等遮挡。
 */
@Composable
fun BoxScope.ScrollToTopFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = 24.dp, bottom = 24.dp + extraBottomPadding),
        containerColor = MiuixTheme.colorScheme.primary,
    ) {
        MiuixIcon(
            painter = painterResource(R.drawable.arrow_upward_outlined),
            contentDescription = "回到顶部",
            tint = MiuixTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}
