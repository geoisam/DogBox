package pjs.dogbox.utils

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
) {
    val formattedDate: String
        get() = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

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
}

const val DISCLAIMER_TITLE = "免责声明"
const val DISCLAIMER_MESSAGE =
    "欢迎您使用 DogBox 软件（以下简称“本软件”）。\n\n本软件只提供聚合展示功能，所有资源来自网络。本软件不参与任何制作、上传、储存、下载等内容。\n\n本软件仅供个人学习与研究参考，严禁用于非法用途，安装后请于 24 小时内删除。\n\n对于使用本软件产生的一切后果，使用者责任自负。"
const val DISCLAIMER_CONFIRM_TEXT = "知道了"

@Composable
fun DisclaimerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
) {
    TipsDialog(
        isOpen = isOpen,
        onClose = onDismiss,
        title = DISCLAIMER_TITLE,
        message = DISCLAIMER_MESSAGE,
        confirmButtonText = DISCLAIMER_CONFIRM_TEXT,
        onConfirm = onDismiss,
        dismissButtonText = null,
        onDismiss = { },
        closeIcon = false,
        onCloseIconClick = { }
    )
}

object SharedData {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_TIPS_DIALOG_SHOWN = "is_tips_dialog_shown"
    private const val KEY_CACHED_CONFIG = "cached_config"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
    private const val KEY_WATCH_HISTORY = "watch_history"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_COLOR_THEME = "color_theme"
    private const val KEY_CUSTOM_PRIMARY_COLOR = "custom_primary_color"
    private const val KEY_DYNAMIC_COLOR_STYLE = "dynamic_color_style"
    private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    private const val KEY_SCROLL_TEXT_CONFIG = "scroll_text_config"

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
            )
        )
    }

    fun getWatchHistory(context: Context): List<WatchHistoryItem> {
        val jsonStr = getPrefs(context).getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            val arr = Json.parseToJsonElement(jsonStr).jsonArray
            arr.mapNotNull { jsonToWatchItem(it.jsonObject) }
                .sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveWatchHistory(context: Context, items: List<WatchHistoryItem>) {
        val arr = JsonArray(items.map { watchItemToJson(it) })
        getPrefs(context).edit { putString(KEY_WATCH_HISTORY, arr.toString()) }
    }

    @Synchronized
    fun addOrUpdateWatchHistory(context: Context, item: WatchHistoryItem) {
        val current = getWatchHistory(context).toMutableList()
        val existingIdx = current.indexOfFirst { it.id == item.id }
        if (existingIdx >= 0) {
            current[existingIdx] = item
        } else {
            current.add(item)
        }
        val trimmed = if (current.size > 200) current.sortedByDescending { it.timestamp }
            .take(200) else current
        saveWatchHistory(context, trimmed)
    }

    @Synchronized
    fun removeWatchHistory(context: Context, ids: Set<String>) {
        val current = getWatchHistory(context).filter { it.id !in ids }
        saveWatchHistory(context, current)
    }

    fun clearWatchHistory(context: Context) {
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

    fun getColorTheme(context: Context): Int {
        return getPrefs(context).getInt(KEY_COLOR_THEME, 1)
    }

    fun setColorTheme(context: Context, theme: Int) {
        getPrefs(context).edit { putInt(KEY_COLOR_THEME, theme) }
    }

    fun getCustomPrimaryColor(context: Context): Color {
        val colorInt = getPrefs(context).getInt(KEY_CUSTOM_PRIMARY_COLOR, 0xFF6750A4.toInt())
        return Color(colorInt)
    }

    fun setCustomPrimaryColor(context: Context, color: Color) {
        val colorInt = ((color.red * 255).toInt() shl 16) or
                ((color.green * 255).toInt() shl 8) or
                ((color.blue * 255).toInt())
        getPrefs(context).edit { putInt(KEY_CUSTOM_PRIMARY_COLOR, colorInt) }
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
}


@Composable
fun SimpleDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = 0.5f))
        )
    }
}