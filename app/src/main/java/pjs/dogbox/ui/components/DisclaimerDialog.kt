package pjs.dogbox.ui.components

import androidx.compose.runtime.Composable

const val DISCLAIMER_TITLE = "免责声明"
const val DISCLAIMER_MESSAGE =
    "欢迎您使用 DogBox 软件（以下简称“本软件”）。\n\n本软件只提供聚合展示功能，所有资源来自网络。本软件不参与任何制作、上传、储存、下载等内容。\n\n本软件仅供个人学习与研究参考，严禁用于非法用途，安装后请于 24 小时内删除。\n\n对于使用本软件产生的一切后果，使用者责任自负。"
const val DISCLAIMER_CONFIRM_TEXT = "知道了"

@Composable
fun DisclaimerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
) {
    TipsDialog(
        isOpen = isOpen,
        onClose = onDismiss,
        title = DISCLAIMER_TITLE,
        message = DISCLAIMER_MESSAGE,
        confirmButtonText = DISCLAIMER_CONFIRM_TEXT,
        onConfirm = onDismiss,
        dismissButtonText = null,
        onDismiss = { },
        closeIcon = false,
        onCloseIconClick = { }
    )
}
