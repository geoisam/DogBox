package pjs.dogbox.ui.components.navbar

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 悬浮底栏的视觉特效。
 *
 * [NONE] 默认材质：原生图标 + 文字的纯色胶囊，不采样背景；
 * [FROSTED_GLASS] 轻透磨砂：miuix-glass 的 Common glass，模糊身后内容并叠加柔和高光；
 * [LIQUID_GLASS] 柔光玻璃：miuix-glass 的 Bionic glass，即 iOS 风格的高饱和、强折射玻璃。
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
