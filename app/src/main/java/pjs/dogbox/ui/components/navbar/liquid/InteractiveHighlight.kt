package pjs.dogbox.ui.components.navbar.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 玻璃按下时的高光反馈：叠加一层加色的高光，并跟随手指位置。
 */
internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (Size, Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private var pointerPosition = Offset.Zero
    private val pressSpec = spring(0.5f, 300f, 0.001f)

    /** 绘制高光的修饰器。 */
    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(Color.White.copy(alpha = 0.05f * progress), blendMode = BlendMode.Plus)
            val p = position(size, pointerPosition)
            val center = Offset(p.x.coerceIn(0f, size.width), p.y.coerceIn(0f, size.height))
            val radius = (size.minDimension * 1.2f).coerceAtLeast(1f)
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.05f * progress),
                        0.5f to Color.White.copy(alpha = 0.05f * progress),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }

    /** 追踪指针的修饰器，用于更新高光位置。 */
    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            pointerPosition = down.position
            animationScope.launch { pressProgressAnimation.animateTo(1f, pressSpec) }
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                pointerPosition = change.position
                if (!change.pressed) break
            }
            animationScope.launch { pressProgressAnimation.animateTo(0f, pressSpec) }
        }
    }

    /** 立即释放高光。 */
    fun release() {
        animationScope.launch { pressProgressAnimation.animateTo(0f, pressSpec) }
    }
}
