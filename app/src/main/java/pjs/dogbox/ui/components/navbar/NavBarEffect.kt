package pjs.dogbox.ui.components.navbar

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 悬浮底栏的视觉特效（三档均为图标 + 文字）。
 *
 * [NONE] 标准材质：不透明 surfaceContainer 纯色胶囊，不采样背景；
 * [FROSTED_GLASS] 轻透磨砂：miuix-glass 的 Common glass，模糊身后内容并叠加柔和高光；
 * [LIQUID_GLASS] 仿 iOS 液态玻璃：miuix 官方示例的 IosLiquidGlassNavigationBar，
 * vibrancy + 边缘折射透镜 + 可拖拽的液态玻璃指示药丸。
 */
enum class NavBarEffect {
    NONE,
    FROSTED_GLASS,
    LIQUID_GLASS;

    companion object {
        /** 将持久化的下标转换为特效枚举，越界时回退到 [NONE]。 */
        fun fromIndex(index: Int): NavBarEffect = entries.getOrElse(index) { NONE }
    }
}

/** 悬浮底栏的单个入口。 */
data class NavBarEntry(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)
