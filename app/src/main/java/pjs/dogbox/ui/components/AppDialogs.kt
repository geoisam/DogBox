package pjs.dogbox.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import pjs.dogbox.BuildConfig
import pjs.dogbox.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun TipsDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    title: String,
    message: String,
    confirmButtonText: String = "确定",
    onConfirm: (() -> Unit)? = null,
    dismissButtonText: String? = "取消",
    onDismiss: (() -> Unit)? = null,
    closeIcon: Boolean,
    onCloseIconClick: (() -> Unit)? = { onClose() }
) {
    OverlayDialog(
        show = isOpen,
        title = title,
        summary = message,
        onDismissRequest = onClose,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (closeIcon && dismissButtonText == null) {
                TextButton(
                    text = "关闭",
                    onClick = { onCloseIconClick?.invoke() },
                )
            }
            dismissButtonText?.let {
                TextButton(
                    text = it,
                    onClick = {
                        onDismiss?.invoke()
                        onClose()
                    },
                )
            }
            TextButton(
                text = confirmButtonText,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    onConfirm?.invoke()
                    onClose()
                },
            )
        }
    }
}

@Composable
fun UpdateBottomSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    latestVersion: String,
    updateNote: String,
    downloadUrl: String,
) {
    val context = LocalContext.current
    AppBottomSheet(
        show = isOpen,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MiuixTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MiuixIcon(
                    painter = painterResource(R.drawable.fiber_new_outlined),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            MiuixText(
                text = "发现新版本",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiuixText(
                            "当前版本",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MiuixText(
                            "v${BuildConfig.VERSION_NAME}",
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                MiuixIcon(
                    painter = painterResource(R.drawable.arrow_right_alt_outlined),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp),
                )
                Card(
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiuixText(
                            "最新版本",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MiuixText(
                            "v$latestVersion",
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                MiuixText(
                    "更新日志",
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(222.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        MiuixText(updateNote, style = MiuixTheme.textStyles.body1)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText("暂时忽略", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText("立即更新", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
