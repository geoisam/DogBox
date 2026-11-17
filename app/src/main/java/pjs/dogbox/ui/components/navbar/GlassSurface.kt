package pjs.dogbox.ui.components.navbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import pjs.dogbox.ui.components.navbar.liquid.lens
import pjs.dogbox.ui.components.navbar.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 毛玻璃与液态玻璃的差异全部集中在这里，避免两种效果只在数值上略有不同、
 * 实际观感却几乎一样：
 *
 * | | 毛玻璃 FROSTED_GLASS | 液态玻璃 LIQUID_GLASS |
 * |---|---|---|
 * | 模糊 | 重度（背后内容压成色块） | 轻度（保留内容轮廓） |
 * | 表面 | 奶白、接近不透明 | 高通透，能看到背后内容 |
 * | 色彩 | 略降饱和，去噪 | 增强饱和度，颜色更"通透" |
 * | 边缘 | 只有玻璃描边高光 | 描边高光 + 折射透镜 + 内阴影 + 投影 |
 *
 * 关键点是亮度/透明度：毛玻璃几乎不透光，液态玻璃必须透光，
 * 否则在纯色背景上两者看起来会一模一样。
 */

/** 毛玻璃的模糊半径（dp）：足够大，把背后内容糊成色块。 */
private const val FrostedBlurDp = 28f

/** 毛玻璃表面不透明度：接近奶白磨砂，只透出一点点颜色。 */
private const val FrostedSurfaceAlphaLight = 0.82f
private const val FrostedSurfaceAlphaDark = 0.76f

/** 液态玻璃的模糊半径（dp）：只做轻微柔化，保留背后内容轮廓。 */
private const val LiquidBlurDp = 3f

/** 液态玻璃的表面不透明度（按明暗主题）：必须明显透光，这是和毛玻璃最大的区别。 */
internal const val LiquidSurfaceAlphaLight = 0.34f
internal const val LiquidSurfaceAlphaDark = 0.28f

/** 液态玻璃表面不透明度，供底栏与指示器共用，保证同一块玻璃的浓度一致。 */
internal fun liquidSurfaceAlpha(isDark: Boolean): Float =
    if (isDark) LiquidSurfaceAlphaDark else LiquidSurfaceAlphaLight

/**
 * 液态玻璃的基础效果：增强饱和度 + 轻度模糊。
 *
 * 底板和指示器都走这套效果，否则指示器会直接铺在未处理的背板上，
 * 在高透明度底板里露出一块"没做玻璃"的清晰区域。
 */
internal fun BackdropEffectScope.liquidGlassBase() {
    vibrancy()
    blur(LiquidBlurDp * density)
}

/** 液态玻璃的边缘折射带宽与位移（dp）：决定玻璃"厚度"的观感。 */
private const val LiquidRefractionHeightDp = 26f
private const val LiquidRefractionAmountDp = 26f

/** 液态玻璃的边缘高光描边。 */
internal val LiquidHighlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.14f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

/**
 * 玻璃表面修饰器。
 *
 * - [NavBarEffect.FROSTED_GLASS]：重度模糊 + 奶白半透明表面，背后内容只剩模糊色块，
 *   只保留一圈玻璃描边高光，不折射。
 * - [NavBarEffect.LIQUID_GLASS]：轻度模糊 + 高通透表面 + 色彩增强 + 边缘折射，
 *   配合调用方叠加的内阴影 / 投影 / 镜面光泽，呈现"能透光、边缘会弯"的液态玻璃。
 *
 * [backdrop] 为 null 或设备不支持运行时着色器时不做任何处理，由调用方回退到纯色背景。
 */
@Composable
internal fun Modifier.glassSurface(
    effect: NavBarEffect,
    backdrop: LayerBackdrop?,
    shape: Shape,
    surfaceColor: Color,
    isDark: Boolean,
    highlight: Highlight? = null,
): Modifier {
    if (effect == NavBarEffect.NONE || backdrop == null || !isRuntimeShaderSupported()) {
        return this
    }
    val densityScale = LocalDensity.current.density
    return when (effect) {
        NavBarEffect.FROSTED_GLASS -> this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 毛玻璃：把背后内容彻底糊掉，再轻微提亮、降饱和，得到奶白磨砂质感。
                blur(FrostedBlurDp * densityScale)
                colorControls(
                    brightness = if (isDark) 0.02f else 0.05f,
                    contrast = 0.94f,
                    saturation = 0.82f,
                )
            },
            highlight = highlightBlock(highlight),
            onDrawSurface = {
                drawRect(
                    surfaceColor.copy(
                        alpha = if (isDark) FrostedSurfaceAlphaDark else FrostedSurfaceAlphaLight,
                    )
                )
            },
        )

        NavBarEffect.LIQUID_GLASS -> this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 液态玻璃：只做轻度柔化，内容轮廓还在，再靠透镜把边缘"折"出玻璃厚度。
                liquidGlassBase()
                lens(
                    refractionHeight = LiquidRefractionHeightDp * densityScale,
                    refractionAmount = LiquidRefractionAmountDp * densityScale,
                )
            },
            highlight = highlightBlock(highlight),
            onDrawSurface = {
                drawRect(
                    surfaceColor.copy(
                        alpha = if (isDark) LiquidSurfaceAlphaDark else LiquidSurfaceAlphaLight,
                    )
                )
            },
        )

        NavBarEffect.NONE -> this
    }
}

/** 把可为空的高光包装成 [drawBackdrop] 需要的接收者 lambda。 */
private fun highlightBlock(highlight: Highlight?): (BackdropEffectScope.() -> Highlight?)? =
    highlight?.let { h -> { h } }

/**
 * 让高光的方向跟随设备倾斜；不支持陀螺仪时保持默认方向。
 */
@Composable
internal fun rememberTiltHighlight(base: Highlight, degrees: Float): Highlight {
    val style = base.style as? BloomStroke ?: return base
    val tilt by rememberDeviceTilt()
    val primary = remember(tilt, style.primaryLight, degrees) {
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val magnitudeSquared = gx * gx + gy * gy
        val magnitude = if (magnitudeSquared > 0.01f) sqrt(magnitudeSquared) else 1f
        val x = if (magnitudeSquared > 0.01f) gx / magnitude else 0f
        val y = if (magnitudeSquared > 0.01f) gy / magnitude else -1f
        val radians = degrees * Math.PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        style.primaryLight.copy(
            position = LightPosition(
                x = 0.5f + c * x - s * y,
                y = 0.7f + s * x + c * y,
                z = style.primaryLight.position.z,
            ),
        )
    }
    return remember(base, primary) { base.copy(style = style.copy(primaryLight = primary)) }
}
