package pjs.dogbox.ui.screens.movie

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import pjs.dogbox.services.MediaPlaybackService

/** 连接 [MediaPlaybackService]，拿到用于 UI 控制的 MediaController */
@androidx.annotation.OptIn(UnstableApi::class)
fun connectMediaController(context: Context): ListenableFuture<MediaController> {
    return MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, MediaPlaybackService::class.java)),
    ).buildAsync()
}

/**
 * 构建带 MediaMetadata 的 MediaItem。
 * 系统媒体控制器（通知栏媒体卡片/锁屏/耳机按键）显示的标题、副标题、封面来自这里。
 */
fun buildPlaybackMediaItem(
    uri: String,
    title: String?,
    subtitle: String?,
    artworkUrl: String?,
): MediaItem {
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title?.takeIf { it.isNotBlank() } ?: "视频播放")
                .setArtist(subtitle?.takeIf { it.isNotBlank() })
                .setArtworkUri(artworkUrl?.takeIf { it.isNotBlank() }?.toUri())
                .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE)
                .build(),
        )
        .build()
}
