package pjs.dogbox.utils.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import pjs.dogbox.data.repository.AppRepository

/** 视频解码方式。软解优先使用软件解码器，失败时自动回退到硬件解码。 */
enum class DecoderMode(val label: String) {
    SOFTWARE("软解（兼容）"),
    HARDWARE("硬解（省电）"),
}

/**
 * 全局解码方式与播放器构建入口。
 *
 * 解码方式只能在播放器创建时决定，因此切换后需要重建 ExoPlayer；
 * [mode] 同时供 [DecoderCodecSelector] 在解码器初始化时读取。
 */
object DecoderModeManager {

    @Volatile
    var mode: DecoderMode = DecoderMode.HARDWARE
        private set

    fun restore(context: Context) {
        mode = DecoderMode.entries.firstOrNull {
            it.name == AppRepository.getDecoderMode(context)
        } ?: DecoderMode.HARDWARE
    }

    fun update(mode: DecoderMode) {
        this.mode = mode
    }

    /**
     * 创建按当前解码方式配置的 ExoPlayer。
     *
     * 使用自定义 RenderersFactory，软解时优先选择软件解码器，并开启解码器回退，
     * 避免部分设备没有对应软件解码器时无法播放。
     */
    @OptIn(UnstableApi::class)
    fun buildPlayer(
        context: Context,
        configure: ExoPlayer.Builder.() -> Unit = {},
    ): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setMediaCodecSelector(DecoderCodecSelector)
            .setEnableDecoderFallback(mode == DecoderMode.SOFTWARE)
        return ExoPlayer.Builder(context, renderersFactory)
            .apply(configure)
            .build()
    }
}

/**
 * 视频软解时优先返回软件解码器；音频始终沿用系统默认选择。
 * [MediaCodecSelector.PREFER_SOFTWARE] 只是调整优先级，没有软件解码器时会保留硬件解码器。
 */
@OptIn(UnstableApi::class)
private object DecoderCodecSelector : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val selector = if (DecoderModeManager.mode == DecoderMode.SOFTWARE &&
            MimeTypes.isVideo(mimeType)
        ) {
            MediaCodecSelector.PREFER_SOFTWARE
        } else {
            MediaCodecSelector.DEFAULT
        }
        return selector.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
    }
}
