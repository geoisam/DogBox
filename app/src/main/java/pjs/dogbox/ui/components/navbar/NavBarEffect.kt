package pjs.dogbox.ui.components.navbar

import androidx.annotation.DrawableRes

/**
 * 悬浮底栏的视觉特效。
 *
 * [NONE] 纯色底栏；[FROSTED_GLASS] 毛玻璃（模糊背景 + 半透明表面）；
 * [LIQUID_GLASS] 液态玻璃（色彩增强 + 背景折射 + 高光描边 + 指示器透镜）。
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
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
)
