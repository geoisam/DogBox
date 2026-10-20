package pjs.dogbox.ui.components.navbar.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/**
 * 提升背板饱和度，让玻璃后面的颜色更通透、更"液态"。
 *
 * 亮度保持不变，仅轻微提高对比度，避免背板发灰。
 */
internal fun BackdropEffectScope.vibrancy(saturation: Float = 1.45f) {
    colorControls(
        brightness = 0f,
        contrast = 1.02f,
        saturation = saturation,
    )
}
