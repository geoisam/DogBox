package pjs.dogbox.ui.screen.mine

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale

data class LocalVideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val path: String = "",
)

data class LocalVideoFolder(
    val uriString: String,
    val name: String,
    val path: String,
)

val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v",
    "3gp", "3gpp", "ts", "m2ts", "rmvb", "rm", "mpg", "mpeg", "vob",
)

const val MAX_SCAN_FILES = 10_000

private val SKIP_DIRS = setOf(
    "Android", "obb", ".thumbnails", "cache", "temp", "Lost.dir", "LOST.DIR",
)

fun isVideoFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.ROOT) in VIDEO_EXTENSIONS

internal fun hasFileAccessPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val read = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        read || write
    }

internal fun openFileAccessPermissionPage(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }.onFailure {
            openPermissionManager(context)
        }
    } else {
        openPermissionManager(context)
    }
}

internal fun uriToDisplayPath(uri: Uri): String {
    if (uri.scheme == "file") {
        return uri.path ?: uri.toString()
    }
    if (uri.scheme == "content") {
        val segments = uri.pathSegments
        val docId = if (segments.size >= 2 && segments[0] == "tree") {
            segments[1]
        } else if (segments.size >= 2 && segments[0] == "document") {
            segments[1]
        } else {
            return uri.toString()
        }
        val decoded = Uri.decode(docId) ?: docId
        val index = decoded.indexOf(':')
        if (index <= 0) return uri.toString()
        val volume = decoded.substring(0, index)
        val relPath = decoded.substring(index + 1).removePrefix("/")
        val base = if (volume == "primary" || volume.startsWith("home")) {
            "/storage/emulated/0"
        } else {
            "/storage/$volume"
        }
        return if (relPath.isEmpty()) base else "$base/$relPath"
    }
    return uri.toString()
}

internal fun defaultVideoFolder(): LocalVideoFolder {
    val dir = File(Environment.getExternalStorageDirectory(), "Download/DogBox")
    val path = dir.absolutePath
    return LocalVideoFolder(
        uriString = "file://$path",
        name = "Download/DogBox",
        path = path,
    )
}

internal fun queryMediaStoreVideos(context: Context): List<LocalVideoItem> {
    val items = mutableListOf<LocalVideoItem>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATA,
    )
    runCatching {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: ""
                val uri = ContentUris.withAppendedId(collection, id)
                items.add(
                    LocalVideoItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "",
                        duration = cursor.getLong(durCol),
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        dateAdded = cursor.getLong(dateCol),
                        path = path,
                    )
                )
            }
        }
    }
    return items
}

internal fun scanFolderVideos(context: Context, folder: LocalVideoFolder): List<LocalVideoItem> {
    val result = mutableListOf<LocalVideoItem>()
    val counter = IntArray(1)
    try {
        val uri = Uri.parse(folder.uriString)
        if (uri.scheme == "file") {
            val dir = uri.path?.let { File(it) } ?: return result
            if (dir.isDirectory) {
                walkFileDir(dir, result, counter)
            }
        } else if (uri.scheme == "content") {
            val tree = DocumentFile.fromTreeUri(context, uri) ?: return result
            walkDocumentDir(tree, result, counter)
        }
    } catch (_: Exception) {
    }
    return result
}

private fun walkFileDir(dir: File, out: MutableList<LocalVideoItem>, counter: IntArray) {
    if (counter[0] >= MAX_SCAN_FILES) return
    val children = try {
        dir.listFiles() ?: return
    } catch (_: Exception) {
        return
    }
    for (child in children) {
        if (counter[0] >= MAX_SCAN_FILES) return
        counter[0]++
        val name = child.name
        if (child.isDirectory) {
            if (name.startsWith(".") || name in SKIP_DIRS) continue
            walkFileDir(child, out, counter)
        } else if (child.isFile && isVideoFile(name)) {
            val abs = child.absolutePath
            out.add(
                LocalVideoItem(
                    id = abs.hashCode().toLong(),
                    uri = Uri.fromFile(child),
                    displayName = name,
                    duration = -1,
                    size = child.length(),
                    width = 0,
                    height = 0,
                    dateAdded = child.lastModified() / 1000L,
                    path = abs,
                )
            )
        }
    }
}

private fun walkDocumentDir(doc: DocumentFile, out: MutableList<LocalVideoItem>, counter: IntArray) {
    if (counter[0] >= MAX_SCAN_FILES) return
    val children = try {
        doc.listFiles()
    } catch (_: Exception) {
        return
    }
    for (child in children) {
        if (counter[0] >= MAX_SCAN_FILES) return
        counter[0]++
        val name = child.name ?: continue
        if (child.isDirectory) {
            if (name.startsWith(".") || name in SKIP_DIRS) continue
            walkDocumentDir(child, out, counter)
        } else if (child.isFile && isVideoFile(name)) {
            val uri = child.uri.toString()
            out.add(
                LocalVideoItem(
                    id = uri.hashCode().toLong(),
                    uri = child.uri,
                    displayName = name,
                    duration = -1,
                    size = child.length(),
                    width = 0,
                    height = 0,
                    dateAdded = child.lastModified() / 1000L,
                    path = uri,
                )
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1024f / 1024f / 1024f)
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024f / 1024f)
        else -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
    }
}
