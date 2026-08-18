package pjs.dogbox.ui.screen.mine

import android.content.Context
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

object LocalVideoStore {
    private const val PREFS_NAME = "local_video_scan"
    private const val KEY_USE_MEDIA_LIBRARY = "use_media_library"
    private const val KEY_FOLDERS = "custom_folders"
    private const val KEY_SCANNED_VIDEOS = "scanned_videos"
    private const val KEY_HAS_SCANNED = "has_scanned"

    private fun prefs(context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // === 使用 Android 媒体库 ===

    fun useMediaLibrary(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_USE_MEDIA_LIBRARY, true)
    }

    fun setUseMediaLibrary(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_USE_MEDIA_LIBRARY, enabled) }
    }

    // === 自定义文件夹 ===

    fun getFolders(context: Context): List<LocalVideoFolder> {
        val json = prefs(context).getString(KEY_FOLDERS, null) ?: run {
            val def = listOf(defaultVideoFolder())
            saveFolders(context, def)
            return def
        }
        return try {
            Json.parseToJsonElement(json).jsonArray.mapNotNull { folderFromJson(it.jsonObject) }
        } catch (_: Exception) {
            listOf(defaultVideoFolder())
        }
    }

    fun saveFolders(context: Context, folders: List<LocalVideoFolder>) {
        val arr = JsonArray(folders.map { folderToJson(it) })
        prefs(context).edit { putString(KEY_FOLDERS, arr.toString()) }
    }

    fun addFolder(context: Context, folder: LocalVideoFolder): List<LocalVideoFolder> {
        val current = getFolders(context).toMutableList()
        if (current.none { it.uriString == folder.uriString }) {
            current.add(folder)
        }
        saveFolders(context, current)
        return current
    }

    fun removeFolder(context: Context, uriString: String): List<LocalVideoFolder> {
        val current = getFolders(context).filter { it.uriString != uriString }
        saveFolders(context, current)
        return current
    }

    // === 扫描结果 ===

    fun hasScanned(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HAS_SCANNED, false)
    }

    fun markScanned(context: Context) {
        prefs(context).edit { putBoolean(KEY_HAS_SCANNED, true) }
    }

    fun getScannedVideos(context: Context): List<LocalVideoItem> {
        val json = prefs(context).getString(KEY_SCANNED_VIDEOS, null) ?: return emptyList()
        return try {
            Json.parseToJsonElement(json).jsonArray.mapNotNull { videoFromJson(it.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveScannedVideos(context: Context, videos: List<LocalVideoItem>) {
        val arr = JsonArray(videos.map { videoToJson(it) })
        prefs(context).edit { putString(KEY_SCANNED_VIDEOS, arr.toString()) }
    }

    fun updateVideo(
        context: Context,
        oldUriString: String,
        newUriString: String,
        newDisplayName: String,
        newPath: String,
    ): List<LocalVideoItem> {
        val current = getScannedVideos(context).toMutableList()
        val idx = current.indexOfFirst {
            it.uri.toString() == oldUriString || it.path == oldUriString
        }
        if (idx >= 0) {
            current[idx] = current[idx].copy(
                uri = android.net.Uri.parse(newUriString),
                displayName = newDisplayName,
                path = newPath,
            )
            saveScannedVideos(context, current)
        }
        return current
    }

    fun removeVideo(context: Context, uriString: String): List<LocalVideoItem> {
        val current = getScannedVideos(context).filter {
            it.uri.toString() != uriString && it.path != uriString
        }
        saveScannedVideos(context, current)
        return current
    }

    // === 序列化 ===

    private fun folderToJson(folder: LocalVideoFolder): JsonObject {
        return JsonObject(
            mapOf(
                "uriString" to JsonPrimitive(folder.uriString),
                "name" to JsonPrimitive(folder.name),
                "path" to JsonPrimitive(folder.path),
            )
        )
    }

    private fun folderFromJson(obj: JsonObject): LocalVideoFolder? {
        return try {
            val uriString = obj["uriString"]?.jsonPrimitive?.contentOrNull ?: return null
            var path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
            if (path.isEmpty() || path.startsWith("content://")) {
                path = uriToDisplayPath(android.net.Uri.parse(uriString))
            }
            LocalVideoFolder(
                uriString = uriString,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                path = path,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun videoToJson(item: LocalVideoItem): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(item.id),
                "uri" to JsonPrimitive(item.uri.toString()),
                "displayName" to JsonPrimitive(item.displayName),
                "duration" to JsonPrimitive(item.duration),
                "size" to JsonPrimitive(item.size),
                "width" to JsonPrimitive(item.width.toLong()),
                "height" to JsonPrimitive(item.height.toLong()),
                "dateAdded" to JsonPrimitive(item.dateAdded),
                "path" to JsonPrimitive(item.path),
            )
        )
    }

    private fun videoFromJson(obj: JsonObject): LocalVideoItem? {
        return try {
            LocalVideoItem(
                id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                uri = android.net.Uri.parse(
                    obj["uri"]?.jsonPrimitive?.contentOrNull ?: return null
                ),
                displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull ?: "",
                duration = obj["duration"]?.jsonPrimitive?.longOrNull ?: -1L,
                size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                width = obj["width"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                height = obj["height"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                dateAdded = obj["dateAdded"]?.jsonPrimitive?.longOrNull ?: 0L,
                path = obj["path"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }
}
