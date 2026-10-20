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
 * - [NavBarEffect.FROSTED_GLASS]：模糊身后的内容并叠加半透明表面。
 * - [NavBarEffect.LIQUID_GLASS]：在毛玻璃基础上追加色彩增强与边缘折射。
 *
 * [backdrop] 为 null 或设备不支持运行时着色器时不做任何处理，由调用方回退到纯色背景。
 */
@Composable
internal fun Modifier.glassSurface(
    effect: NavBarEffect,
    backdrop: LayerBackdrop?,
    shape: Shape,
    surfaceColor: Color,
    blurRadius: Float = 24f,
    tintAlpha: Float = 0.52f,
    highlight: Highlight? = null,
): Modifier {
    if (effect == NavBarEffect.NONE || backdrop == null || !isRuntimeShaderSupported()) {
        return this
    }
    val densityScale = LocalDensity.current.density
    val highlightBlock: (BackdropEffectScope.() -> Highlight?)? = highlight?.let { h -> { h } }
    return if (effect == NavBarEffect.LIQUID_GLASS) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(6f * densityScale)
                lens(22f * densityScale, 22f * densityScale)
            },
            highlight = highlightBlock,
            onDrawSurface = { drawRect(surfaceColor.copy(alpha = tintAlpha)) },
        )
    } else {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(blurRadius * densityScale)
            },
            highlight = highlightBlock,
            onDrawSurface = { drawRect(surfaceColor.copy(alpha = tintAlpha)) },
        )
    }
}

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
