package pjs.dogbox.ui.components.navbar

import androidx.annotation.DrawableRes

/**
 * 悬浮底栏的视觉特效。
 *
 * [NONE] 纯色底栏；
 * [FROSTED_GLASS] 毛玻璃：重度模糊 + 奶白半透明表面，背后内容糊成色块，只有描边高光；
 * [LIQUID_GLASS] 液态玻璃：轻度模糊 + 高通透表面 + 色彩增强，额外叠加边缘折射、
 * 内阴影、投影与镜面光泽，能透出背后内容且边缘有"弯曲"的玻璃厚度。
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
