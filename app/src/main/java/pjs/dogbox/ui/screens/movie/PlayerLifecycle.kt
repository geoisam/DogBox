package pjs.dogbox.ui.screens.movie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player

/**
 * 所有视频播放器统一的后台播放控制。
 *
 * - 当 [GlobalPlayerConfig.backgroundPlayback] 为 false 时，
 *   Activity 进入后台（ON_PAUSE / ON_STOP）必须暂停播放。
 * - 必须无条件调用 pause()，而不能只判断 isPlaying，
 *   否则缓冲中（playWhenReady=true 但 isPlaying=false）切后台，
 *   缓冲完成后仍会在后台自动出声。
 * - 当开关为 true 时不做任何处理，保持后台继续播放。
 */
@Composable
fun PauseOnBackground(exoPlayer: Player?) {
    // 直接读 State，保证开关即时生效
    val backgroundPlayback = GlobalPlayerConfig.backgroundPlayback
    val currentBackgroundPlayback by rememberUpdatedState(backgroundPlayback)
    val context = LocalContext.current
    // 用 Activity 的 Lifecycle，而不是 NavBackStackEntry 的，
    // 否则按 Home / 锁屏 / 切应用时收不到回调。
    val lifecycleOwner = rememberUpdatedState(context as? LifecycleOwner).value

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer
        if (lifecycleOwner == null || player == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    if (!currentBackgroundPlayback) {
                        // playWhenReady 为 true（含缓冲中）也要停，否则后台会自动出声
                        if (player.playWhenReady || player.isPlaying) {
                            player.pause()
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
}
