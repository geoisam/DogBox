package pjs.dogbox.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

const val DISCLAIMER_TITLE = "免责声明"
const val DISCLAIMER_MESSAGE =
    "本软件为开源免费软件，仅供个人学习、研究、参考等非商业性个人测试使用，严禁用于任何非法用途。本软件仅提供资源聚合与展示功能，不参与任何内容的制作、上传、存储或下载，所有资源均来源于网络。对于使用本软件产生的一切风险和后果与本软件无关，使用者责任自负。“同意”即表示您已阅读并完全理解本声明；如不同意，请退出并停止使用！"

@Composable
fun DisclaimerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    OverlayDialog(
        show = isOpen,
        title = DISCLAIMER_TITLE,
        summary = DISCLAIMER_MESSAGE,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "退出",
                onClick = { context.findActivity()?.finishAffinity() },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "同意",
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 沿 ContextWrapper 链查找宿主 Activity，用于“退出”按钮结束整个任务。 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
