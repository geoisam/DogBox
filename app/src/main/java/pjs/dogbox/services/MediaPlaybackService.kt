package pjs.dogbox.services

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import pjs.dogbox.MainActivity

/**
 * 视频播放前台服务：由服务持有一个真实 ExoPlayer，并用 MediaSession 暴露给系统。
 *
 * - UI（MovieDetailScreen）通过 MediaController 连接本服务下发播放控制指令；
 * - media3 自动维护带媒体样式的通知，系统媒体控制器（通知栏/快捷设置媒体卡片、
 *   锁屏、耳机与蓝牙按键）即可显示标题、集数、封面等播放信息；
 * - 全局设置 backgroundPlayback = true 时，应用退到后台播放仍在继续。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .build()
        // 让系统媒体卡片点击后能回到应用
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(connectionInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0
            || player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
