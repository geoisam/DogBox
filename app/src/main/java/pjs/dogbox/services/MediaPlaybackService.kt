package pjs.dogbox.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import pjs.dogbox.MainActivity
import pjs.dogbox.R
import pjs.dogbox.utils.player.DecoderModeManager

/**
 * 视频播放前台服务：由服务持有一个真实 ExoPlayer，并用 MediaSession 暴露给系统。
 *
 * - UI（MovieDetailScreen）通过 MediaController 连接本服务下发播放控制指令；
 * - media3 自动维护带媒体样式的通知，系统媒体控制器（通知栏/快捷设置媒体卡片、
 *   锁屏、耳机与蓝牙按键）即可显示标题、集数、封面等播放信息；
 * - 只有"正在播放（含缓冲）"时才保留前台通知：暂停后撤掉通知，状态栏与
 *   「灵动岛」胶囊不再残留，但 MediaSession 保持活跃，系统控制栏的媒体卡片仍可继续操作；
 * - 全局设置 backgroundPlayback = true 时，应用退到后台播放仍在继续。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var currentPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        DecoderModeManager.restore(this)
        val player = createPlayer()
        currentPlayer = player
        instance = this
        // 让系统媒体卡片点击后能回到应用
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // 系统控制栏（通知、锁屏、快捷设置媒体卡片）的按钮顺序由媒体按钮偏好决定：
        // 「后退 10 秒」放后槽、「快进 10 秒」放前槽，中间的播放/暂停按钮仍由 media3
        // 根据当前播放状态自动生成，于是三个按钮自然夹在播放键左右两侧。
        // 再配合 PlaybackOnlyPlayer 隐藏的上一集/下一集命令，控制栏里就不会出现切集按钮。
        mediaSession = MediaSession.Builder(this, PlaybackOnlyPlayer(player))
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                        .setDisplayName(getString(R.string.media_control_rewind_10))
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                        .setDisplayName(getString(R.string.media_control_forward_10))
                        .build(),
                )
            )
            .build()
    }

    private fun createPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        return DecoderModeManager.buildPlayer(this) {
            setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            // 系统控制栏的快退/快进按钮走 player.seekBack()/seekForward()，
            // 步长由播放器决定（默认 5 秒），这里统一成 10 秒，与按钮图标一致。
            setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        }
    }

    /**
     * 解码方式切换后重建底层 ExoPlayer，并把当前媒体、进度和播放状态迁移到新播放器。
     * MediaSession 的控制器连接保持不变，UI 无需重新连接。
     */
    private fun rebuildPlayerForDecoderMode() {
        val session = mediaSession ?: return
        val oldPlayer = currentPlayer ?: return
        val mediaItemCount = oldPlayer.mediaItemCount
        val mediaItems = List(mediaItemCount) { oldPlayer.getMediaItemAt(it) }
        val currentMediaItemIndex = oldPlayer.currentMediaItemIndex
        val position = oldPlayer.currentPosition
        val playWhenReady = oldPlayer.playWhenReady
        val repeatMode = oldPlayer.repeatMode
        val shuffleModeEnabled = oldPlayer.shuffleModeEnabled
        val volume = oldPlayer.volume
        val playbackParameters = oldPlayer.playbackParameters

        val newPlayer = createPlayer()
        newPlayer.repeatMode = repeatMode
        newPlayer.shuffleModeEnabled = shuffleModeEnabled
        newPlayer.volume = volume
        newPlayer.playbackParameters = playbackParameters
        if (mediaItems.isNotEmpty()) {
            newPlayer.setMediaItems(
                mediaItems,
                currentMediaItemIndex.coerceIn(0, mediaItems.lastIndex),
                position.coerceAtLeast(0L),
            )
            newPlayer.prepare()
        }
        newPlayer.playWhenReady = playWhenReady
        session.setPlayer(PlaybackOnlyPlayer(newPlayer))
        currentPlayer = newPlayer
        oldPlayer.release()
    }

    override fun onGetSession(connectionInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * 只在「正在播放 / 缓冲中」时才发通知。
     *
     * 判断依据是播放器自身状态，而不是入参 startInForegroundRequired：
     * media3 为了让"刚按过暂停"的用户还能从通知栏继续操作，会在暂停后继续把服务留在前台
     * 一段时间（user-engaged 超时），此时入参仍然是 true，光看它就撤不掉通知。
     *
     * 暂停（或播放结束）后：
     * - 停掉前台、取消通知 → 状态栏图标与「灵动岛」胶囊消失；
     * - MediaSession 不释放 → 快捷设置的媒体卡片仍在，可以直接点播放恢复。
     *
     * 注意这里「不调用 super」而不是「先 super 再取消」：media3 的 updateNotification
     * 是异步的（返回 ListenableFuture），先 super 再 cancel 有可能 cancel 走在发通知之前，
     * 通知反而会留下来。
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val player = session.player
        val playing = player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
        if (playing) {
            super.onUpdateNotification(session, startInForegroundRequired)
            return
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)
            ?.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
    }

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
        currentPlayer = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** 系统控制栏快退/快进一步的时长。 */
        private const val SEEK_INCREMENT_MS = 10_000L

        @Volatile
        private var instance: MediaPlaybackService? = null

        /**
         * 解码方式变更时通知正在运行的服务立即重建播放器。
         *
         * 重建必须发生在主线程（MediaSession.setPlayer 与 ExoPlayer 构建都要求
         * session 的 application looper），调用方可能是任意线程，这里统一切到主线程。
         */
        fun notifyDecoderModeChanged() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                service.rebuildPlayerForDecoderMode()
            }
        }
    }
}

/**
 * 只保留「播放 / 暂停」与「快退 / 快进 10 秒」的播放器包装。
 *
 * media3 的通知、锁屏与快捷设置媒体卡片都按 [Player.getAvailableCommands] 渲染按钮，
 * 屏蔽掉跳集相关命令后，系统控制栏里就只剩播放/暂停以及位于其左右两侧的
 * 后退/快进 10 秒按钮（进度条仍然保留）。
 *
 * 命令必须每次动态计算：ExoPlayer 的可用命令会随播放状态变化（例如开始播放后才有
 * seek 类命令），如果在构造时缓存快照，会连进度拖动一起丢掉。
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class PlaybackOnlyPlayer(player: Player) : ForwardingPlayer(player) {

    private val hiddenCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands()
        .buildUpon()
        .removeAll(*hiddenCommands)
        .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command !in hiddenCommands && super.isCommandAvailable(command)

    // 兜底：即使某些系统 UI 仍然发出跳集指令，也不要真的切集
    override fun seekToNext() = Unit

    override fun seekToPrevious() = Unit

    override fun seekToNextMediaItem() = Unit

    override fun seekToPreviousMediaItem() = Unit
}
