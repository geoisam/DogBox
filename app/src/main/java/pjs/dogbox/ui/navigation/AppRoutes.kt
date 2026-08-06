package pjs.dogbox.ui.navigation

import android.net.Uri

object AppRoutes {
    const val MAIN = "main"
    const val DISCOVER_DETAIL = "discover/{page}"
    const val SEARCH = "search?query={query}"
    const val PLAY = "play?url={url}&position={position}&episode={episode}"
    const val WATCH_HISTORY = "watch_history"
    const val MY_THEME = "my_theme"
    const val ABOUT_ME = "about_me"
    const val SPONSOR = "sponsor"
    const val CONTRIBUTOR = "contributor"
    const val LICENSE = "license"

    fun discoverDetail(page: String): String = "discover/$page"

    fun search(query: String): String = "search?query=${Uri.encode(query)}"

    fun play(url: String, position: Long, episode: String?): String =
        "play?url=${Uri.encode(url)}&position=$position&episode=${Uri.encode(episode ?: "")}"
}
