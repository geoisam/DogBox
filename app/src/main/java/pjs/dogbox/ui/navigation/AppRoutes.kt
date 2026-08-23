package pjs.dogbox.ui.navigation

import android.net.Uri

object AppRoutes {
    const val MAIN = "main"
    const val DISCOVER_DETAIL = "discover/{page}"
    const val SEARCH = "search?query={query}"
    const val PLAY = "play?url={url}&position={position}&episode={episode}"
    const val WATCH_HISTORY = "watch_history"
    const val LOCAL_VIDEOS = "local_videos"
    const val LOCAL_PLAY = "local_play?url={url}&title={title}"
    const val ANALYZE_PLAY = "analyze_play?urls={urls}&index={index}&title={title}&referer={referer}"
    const val ANALYZE_IMAGE_GALLERY = "analyze_image_gallery?urls={urls}&index={index}&referer={referer}&title={title}"
    const val ANALYZE_RESULT = "analyze_result?type={type}&urls={urls}&referer={referer}&title={title}"
    const val MY_THEME = "my_theme"
    const val ABOUT_ME = "about_me"
    const val SPONSOR = "sponsor"
    const val CONTRIBUTOR = "contributor"
    const val LICENSE = "license"
    const val IMAGE_VIEWER = "image_viewer?url={url}&referer={referer}&title={title}"
    const val SETTINGS = "settings"
    const val CACHE_MANAGE = "cache_manage"


    fun discoverDetail(page: String): String = "discover/$page"

    fun search(query: String): String = "search?query=${Uri.encode(query)}"

    fun play(url: String, position: Long, episode: String?): String =
        "play?url=${Uri.encode(url)}&position=$position&episode=${Uri.encode(episode ?: "")}"

    fun imageViewer(url: String, referer: String, title: String): String =
        "image_viewer?url=${Uri.encode(url)}&referer=${Uri.encode(referer)}&title=${Uri.encode(title)}"

    fun localPlay(url: String, title: String): String =
        "local_play?url=${Uri.encode(url)}&title=${Uri.encode(title)}"

    fun analyzePlay(urls: List<String>, index: Int, title: String, referer: String): String =
        "analyze_play?urls=${Uri.encode(urls.joinToString("\n"))}&index=$index&title=${Uri.encode(title)}&referer=${Uri.encode(referer)}"

    fun analyzeImageGallery(urls: List<String>, index: Int, referer: String, title: String): String =
        "analyze_image_gallery?urls=${Uri.encode(urls.joinToString("\n"))}&index=$index&referer=${Uri.encode(referer)}&title=${Uri.encode(title)}"

    fun analyzeResult(type: String, urls: List<String>, referer: String, title: String): String =
        "analyze_result?type=${Uri.encode(type)}&urls=${Uri.encode(urls.joinToString("\n"))}&referer=${Uri.encode(referer)}&title=${Uri.encode(title)}"
}
