package pjs.dogbox.ui.screens.mine

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pjs.dogbox.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun SponsorScreen(
    onBack: () -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val notice = "此应用程序完全免费，感谢您的支持"
    val context = LocalContext.current

    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = "捐赠支持",
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.primaryContainer,
                    ),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.app_badging_outlined),
                            contentDescription = "通知",
                            tint = MiuixTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MiuixText(
                            text = notice,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            items(count = 2) { index ->
                val assetName = if (index == 0) "alipay" else "wechat"
                val resTitle = if (index == 0) "支付宝" else "微信"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(0.5f),
                    onClick = {
                        onViewImage(
                            "file:///android_asset/img/$assetName.jpg",
                            "",
                            resTitle,
                        )
                    },
                    insideMargin = PaddingValues(12.dp),
                ) {
                    val bitmap = rememberAssetImage(context, "img/$assetName.jpg")
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = resTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun rememberAssetImage(context: Context, assetPath: String): ImageBitmap? =
    remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use { it.readBytes() }
                .let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .asImageBitmap()
        }.getOrNull()
    }
