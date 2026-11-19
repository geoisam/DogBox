package pjs.dogbox.ui.components

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import pjs.dogbox.data.repository.AppRepository
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
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

/** Miuix 子页面脚手架：统一返回栏 + 内容内边距 */
@Composable
fun MiuixSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    MiuixScaffold(
        topBar = {
            GlassTopAppBar(
                expandable = false,
                bandBrush = if (AppRepository.getAdvancedMaterial(LocalContext.current)) {
                    GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
                } else {
                    SolidColor(MiuixTheme.colorScheme.surface)
                },

                fill = if (AppRepository.getAdvancedMaterial(LocalContext.current)) GlassTopAppBarDefaults.buttonFill() else Color.Transparent,

                title = title,
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Glass.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
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
