package pjs.dogbox.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import pjs.dogbox.data.model.AppConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AppRepository {

    data class WatchHistoryItem(
        val id: String,
        val url: String,
        val coverUrl: String,
        val title: String,
        val progress: Long,
        val duration: Long,
        val timestamp: Long,
        val orientation: Int = 0,
        val episodeName: String = "",
        val key: String = AppConfig.YXXQ_KEY,
    ) {
        // host 不再持久化，按 key 动态获取
        val host: String
            get() = AppConfig.hostForKey(key)
        val formattedDate: String
            get() = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER)
        val formattedTime: String
            get() = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(TIME_FORMATTER)

        val progressPercent: Float
            get() = if (duration > 0) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f

        val progressText: String
            get() {
                val p = progress / 1000
                val d = duration / 1000
                val pMin = p / 60
                val pSec = p % 60
                val dMin = d / 60
                val dSec = d % 60
                return String.format("%02d:%02d / %02d:%02d", pMin, pSec, dMin, dSec)
            }

        private companion object {
            val DATE_TIME_FORMATTER: DateTimeFormatter =
                DateTimeFormatter.ofPattern("yy-MM-dd HH:mm", Locale.getDefault())
            val TIME_FORMATTER: DateTimeFormatter =
                DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        }
    }

    private const val PREFS_NAME = "app_settings"
    private const val KEY_TIPS_DIALOG_SHOWN = "is_tips_dialog_shown"
    private const val KEY_CACHED_CONFIG = "cached_config"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
    private const val KEY_WATCH_HISTORY = "watch_history"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_DYNAMIC_COLOR_STYLE = "dynamic_color_style"
    private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    private const val KEY_SCROLL_TEXT_CONFIG = "scroll_text_config"
    private const val KEY_DOWNLOAD_MODE = "download_mode"
    private const val KEY_LONG_PRESS_SPEED = "long_press_speed"
    private const val KEY_BACKGROUND_PLAYBACK = "background_playback"
    private const val KEY_ADS_FILTER = "ads_filter"

    private const val KEY_FLOATING_NAV_BAR = "floating_nav_bar"
    private const val KEY_NAV_BAR_EFFECT = "nav_bar_effect"
    private const val KEY_HIDE_NAV_BAR_DIVIDER = "hide_nav_bar_divider"

    private const val KEY_PLAYBACK_ORDER = "playback_order"

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstLaunch(context: Context): Boolean {
        return !getPrefs(context).getBoolean(KEY_TIPS_DIALOG_SHOWN, false)
    }

    fun markTipsDialogShown(context: Context) {
        getPrefs(context).edit { putBoolean(KEY_TIPS_DIALOG_SHOWN, true) }
    }

    fun saveConfigCache(context: Context, configJson: String) {
        getPrefs(context).edit {
            putString(KEY_CACHED_CONFIG, configJson)
            putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
        }
    }

    fun getCachedConfig(context: Context): String? {
        return getPrefs(context).getString(KEY_CACHED_CONFIG, null)
    }

    fun getCacheTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_CACHE_TIMESTAMP, 0L)
    }

    // === Watch History ===

    private fun jsonToWatchItem(obj: JsonObject): WatchHistoryItem? {
        return try {
            val key = obj["key"]?.jsonPrimitive?.contentOrNull
                ?: obj["host"]?.jsonPrimitive?.contentOrNull?.let { AppConfig.sourceKey(it) }
                ?: AppConfig.YXXQ_KEY
            WatchHistoryItem(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                url = obj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                coverUrl = obj["coverUrl"]?.jsonPrimitive?.contentOrNull ?: "",
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                progress = obj["progress"]?.jsonPrimitive?.longOrNull ?: 0L,
                duration = obj["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
                timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull
                    ?: System.currentTimeMillis(),
                orientation = obj["orientation"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                episodeName = obj["episodeName"]?.jsonPrimitive?.contentOrNull ?: "",
                key = key,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun watchItemToJson(item: WatchHistoryItem): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(item.id),
                "url" to JsonPrimitive(item.url),
                "coverUrl" to JsonPrimitive(item.coverUrl),
                "title" to JsonPrimitive(item.title),
                "progress" to JsonPrimitive(item.progress),
                "duration" to JsonPrimitive(item.duration),
                "timestamp" to JsonPrimitive(item.timestamp),
                "orientation" to JsonPrimitive(item.orientation.toLong()),
                "episodeName" to JsonPrimitive(item.episodeName),
                "key" to JsonPrimitive(item.key),
            )
        )
    }

    private var cachedWatchHistory: MutableList<WatchHistoryItem>? = null
    private var lastHistoryPersistTs = 0L

    private const val HISTORY_PERSIST_DEBOUNCE_MS = 30_000L

    private fun loadWatchHistory(context: Context): List<WatchHistoryItem> {
        val jsonStr = getPrefs(context).getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            val arr = Json.parseToJsonElement(jsonStr).jsonArray
            arr.mapNotNull { jsonToWatchItem(it.jsonObject) }
                // 已下线来源的历史记录不再展示
                .filterNot { it.key == "NIVOD" || it.key == "YLSP" }
                .sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun ensureWatchHistoryLoaded(context: Context): MutableList<WatchHistoryItem> {
        cachedWatchHistory?.let { return it }
        return loadWatchHistory(context).toMutableList().also { cachedWatchHistory = it }
    }

    @Synchronized
    fun getWatchHistory(context: Context): List<WatchHistoryItem> =
        ensureWatchHistoryLoaded(context).sortedByDescending { it.timestamp }

    private fun persistWatchHistory(context: Context) {
        val items = cachedWatchHistory ?: return
        val arr = JsonArray(items.map { watchItemToJson(it) })
        getPrefs(context).edit { putString(KEY_WATCH_HISTORY, arr.toString()) }
    }

    @Synchronized
    private fun persistWatchHistoryDebounced(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastHistoryPersistTs < HISTORY_PERSIST_DEBOUNCE_MS) return
        lastHistoryPersistTs = now
        persistWatchHistory(context)
    }

    /** 立即将内存中的观看历史落盘，用于退出播放等需要保证进度的时机 */
    @Synchronized
    fun flushWatchHistory(context: Context) {
        lastHistoryPersistTs = 0L
        persistWatchHistory(context)
    }

    @Synchronized
    fun addOrUpdateWatchHistory(context: Context, item: WatchHistoryItem) {
        val current = ensureWatchHistoryLoaded(context)
        val existingIdx = current.indexOfFirst { it.id == item.id && it.key == item.key }
        if (existingIdx >= 0) {
            current[existingIdx] = item
        } else {
            current.add(item)
        }
        current.sortByDescending { it.timestamp }
        if (current.size > 200) {
            val trimmed = current.sortedByDescending { it.timestamp }
                .take(200).toMutableList()
            current.clear()
            current.addAll(trimmed)
        }
        persistWatchHistoryDebounced(context)
    }

    @Synchronized
    fun removeWatchHistory(context: Context, ids: Set<String>) {
        val current = ensureWatchHistoryLoaded(context)
        current.removeAll { "${it.key}|${it.id}" in ids }
        persistWatchHistory(context)
    }

    @Synchronized
    fun clearWatchHistory(context: Context) {
        cachedWatchHistory?.clear()
        cachedWatchHistory = mutableListOf()
        getPrefs(context).edit { remove(KEY_WATCH_HISTORY) }
    }

    // === Theme ===

    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, 0)
    }

    fun setThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit { putInt(KEY_THEME_MODE, mode) }
    }

    fun getDynamicColor(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
    }

    fun getDynamicColorStyle(context: Context): String {
        return getPrefs(context).getString(KEY_DYNAMIC_COLOR_STYLE, "TonalSpot") ?: "TonalSpot"
    }

    fun setDynamicColorStyle(context: Context, style: String) {
        getPrefs(context).edit { putString(KEY_DYNAMIC_COLOR_STYLE, style) }
    }

    // === Auto Check Update ===

    fun isAutoCheckUpdate(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CHECK_UPDATE, true)
    }

    fun setAutoCheckUpdate(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_AUTO_CHECK_UPDATE, enabled) }
    }

    private var autoCheckDoneState: Boolean = false

    fun isAutoCheckDoneThisLaunch(): Boolean = autoCheckDoneState

    fun markAutoCheckDone() {
        autoCheckDoneState = true
    }

    fun resetAutoCheckDone() {
        autoCheckDoneState = false
    }

    // === Download Mode ===
    // 0 = built-in download (default), 1 = system download manager
    fun getDownloadMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_DOWNLOAD_MODE, 0)
    }

    fun setDownloadMode(context: Context, mode: Int) {
        getPrefs(context).edit { putInt(KEY_DOWNLOAD_MODE, mode) }
    }

    // === Long Press Speed ===

    fun getLongPressSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_LONG_PRESS_SPEED, 3.0f)
    }

    fun setLongPressSpeed(context: Context, speed: Float) {
        getPrefs(context).edit { putFloat(KEY_LONG_PRESS_SPEED, speed) }
    }

    // === Background Playback ===

    fun isBackgroundPlayback(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BACKGROUND_PLAYBACK, false)
    }

    fun setBackgroundPlayback(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_BACKGROUND_PLAYBACK, enabled) }
    }

    // === Ads Filter ===

    fun isAdsFilterEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ADS_FILTER, true)
    }

    fun setAdsFilterEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_ADS_FILTER, enabled) }
    }

    // === Floating Navigation Bar ===

    /** 底栏设置（是否悬浮、特效类型、是否隐藏分割线），通过 [navBarSettings] 暴露给 UI 层实时响应。 */
    data class NavBarSettings(
        val floating: Boolean = true,
        val effect: Int = 0,
        val hideDivider: Boolean = false,
    )

    private val _navBarSettings = MutableStateFlow(NavBarSettings())
    val navBarSettings: StateFlow<NavBarSettings> = _navBarSettings.asStateFlow()
    private var navBarSettingsLoaded = false

    /** 首次读取时把持久化设置同步到 [navBarSettings]。 */
    fun loadNavBarSettings(context: Context) {
        if (navBarSettingsLoaded) return
        navBarSettingsLoaded = true
        _navBarSettings.value = NavBarSettings(
            floating = isFloatingNavBarEnabled(context),
            effect = getNavBarEffect(context),
            hideDivider = isNavBarDividerHidden(context),
        )
    }

    fun isFloatingNavBarEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FLOATING_NAV_BAR, true)
    }

    fun setFloatingNavBarEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_FLOATING_NAV_BAR, enabled) }
        _navBarSettings.value = _navBarSettings.value.copy(floating = enabled)
    }

    fun getNavBarEffect(context: Context): Int {
        val stored = getPrefs(context).getInt(KEY_NAV_BAR_EFFECT, -1)
        return if (stored >= 0) stored
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) 1 else 0
    }

    fun setNavBarEffect(context: Context, effect: Int) {
        getPrefs(context).edit { putInt(KEY_NAV_BAR_EFFECT, effect) }
        _navBarSettings.value = _navBarSettings.value.copy(effect = effect)
    }

    /** 停靠式默认导航栏是否隐藏顶部分割线（仅悬浮底栏关闭时可见的设置项）。 */
    fun isNavBarDividerHidden(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HIDE_NAV_BAR_DIVIDER, false)
    }

    fun setNavBarDividerHidden(context: Context, hidden: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_HIDE_NAV_BAR_DIVIDER, hidden) }
        _navBarSettings.value = _navBarSettings.value.copy(hideDivider = hidden)
    }

    // === Playback Order ===

    fun getPlaybackOrder(context: Context): Int {
        return getPrefs(context).getInt(KEY_PLAYBACK_ORDER, 0)
    }

    fun setPlaybackOrder(context: Context, order: Int) {
        getPrefs(context).edit { putInt(KEY_PLAYBACK_ORDER, order) }
    }

    // === Scrolling Text Config ===

    data class ScrollingTextConfig(
        val text: String = "我爱你",
        val direction: Int = 0,
        val speedSeconds: Int = 10,
        val fontSizeSp: Int = 250,
        val letterSpacingSp: Int = 0,
        val lineHeightSp: Int = 0,
        val horizontalAlignment: Int = 1,
        val verticalAlignment: Int = 1,
        val isBold: Boolean = true,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val bgColor: Int = 0xFF000000.toInt(),
        val textColor: Int = 0xFFFFFFFF.toInt(),
    )

    fun getScrollingTextConfig(context: Context): ScrollingTextConfig {
        val jsonStr = getPrefs(context).getString(KEY_SCROLL_TEXT_CONFIG, null)
            ?: return ScrollingTextConfig()
        return try {
            val obj = Json.parseToJsonElement(jsonStr).jsonObject
            ScrollingTextConfig(
                text = obj["text"]?.jsonPrimitive?.contentOrNull ?: "我爱你",
                direction = obj["direction"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                speedSeconds = obj["speedSeconds"]?.jsonPrimitive?.longOrNull?.toInt() ?: 10,
                fontSizeSp = obj["fontSizeSp"]?.jsonPrimitive?.longOrNull?.toInt() ?: 250,
                letterSpacingSp = obj["letterSpacingSp"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                lineHeightSp = obj["lineHeightSp"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                horizontalAlignment = obj["horizontalAlignment"]?.jsonPrimitive?.longOrNull?.toInt()
                    ?: 1,
                verticalAlignment = obj["verticalAlignment"]?.jsonPrimitive?.longOrNull?.toInt()
                    ?: 1,
                isBold = obj["isBold"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true,
                isItalic = obj["isItalic"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false,
                isUnderline = obj["isUnderline"]?.jsonPrimitive?.contentOrNull?.toBoolean()
                    ?: false,
                bgColor = obj["bgColor"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0xFF000000.toInt(),
                textColor = obj["textColor"]?.jsonPrimitive?.longOrNull?.toInt()
                    ?: 0xFFFFFFFF.toInt(),
            )
        } catch (_: Exception) {
            ScrollingTextConfig()
        }
    }

    fun saveScrollingTextConfig(context: Context, config: ScrollingTextConfig) {
        val obj = JsonObject(
            mapOf(
                "text" to JsonPrimitive(config.text),
                "direction" to JsonPrimitive(config.direction.toLong()),
                "speedSeconds" to JsonPrimitive(config.speedSeconds.toLong()),
                "fontSizeSp" to JsonPrimitive(config.fontSizeSp.toLong()),
                "letterSpacingSp" to JsonPrimitive(config.letterSpacingSp.toLong()),
                "lineHeightSp" to JsonPrimitive(config.lineHeightSp.toLong()),
                "horizontalAlignment" to JsonPrimitive(config.horizontalAlignment.toLong()),
                "verticalAlignment" to JsonPrimitive(config.verticalAlignment.toLong()),
                "isBold" to JsonPrimitive(config.isBold),
                "isItalic" to JsonPrimitive(config.isItalic),
                "isUnderline" to JsonPrimitive(config.isUnderline),
                "bgColor" to JsonPrimitive(config.bgColor.toLong()),
                "textColor" to JsonPrimitive(config.textColor.toLong()),
            )
        )
        getPrefs(context).edit { putString(KEY_SCROLL_TEXT_CONFIG, obj.toString()) }
    }

    // === Cached Update Info (in-memory, resets on app restart) ===

    data class CachedUpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val note: String,
        val downloadUrl: String,
    )

    private var cachedUpdateInfoState: CachedUpdateInfo? by mutableStateOf(null)

    fun getCachedUpdateInfo(): CachedUpdateInfo? = cachedUpdateInfoState

    fun setCachedUpdateInfo(info: CachedUpdateInfo?) {
        cachedUpdateInfoState = info
    }

    private var cachedUpToDateState: Boolean by mutableStateOf(false)

    fun isCachedUpToDate(): Boolean = cachedUpToDateState

    fun setCachedUpToDate(value: Boolean) {
        cachedUpToDateState = value
    }

    private var cachedCheckFailedState: Boolean by mutableStateOf(false)

    fun isCachedCheckFailed(): Boolean = cachedCheckFailedState

    fun setCachedCheckFailed(value: Boolean) {
        cachedCheckFailedState = value
    }

}