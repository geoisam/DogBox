package pjs.dogbox.ui.navigation

import android.net.Uri

object AppRoutes {
    const val MAIN = "main"
    const val DISCOVER_DETAIL = "discover/{page}"
    const val SEARCH = "search?query={query}"
    const val PLAY = "play?url={url}&position={position}&episode={episode}"
    const val WATCH_HISTORY = "watch_history"
    const val LOCAL_VIDEOS = "local_videos"
    const val MEDIA_SCANNER = "media_scanner"
    const val LOCAL_PLAY = "local_play?url={url}&title={title}"
    const val MY_THEME = "my_theme"
    const val ABOUT_ME = "about_me"
    const val SPONSOR = "sponsor"
    const val CONTRIBUTOR = "contributor"
    const val LICENSE = "license"
    const val IMAGE_VIEWER = "image_viewer?url={url}&referer={referer}&title={title}"

    fun discoverDetail(page: String): String = "discover/$page"

    fun search(query: String): String = "search?query=${Uri.encode(query)}"

    fun play(url: String, position: Long, episode: String?): String =
        "play?url=${Uri.encode(url)}&position=$position&episode=${Uri.encode(episode ?: "")}"

    fun imageViewer(url: String, referer: String, title: String): String =
        "image_viewer?url=${Uri.encode(url)}&referer=${Uri.encode(referer)}&title=${Uri.encode(title)}"

    fun localPlay(url: String, title: String): String =
        "local_play?url=${Uri.encode(url)}&title=${Uri.encode(title)}"
}
