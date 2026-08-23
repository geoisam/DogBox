package pjs.dogbox.service

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.MainActivity
import pjs.dogbox.R
import pjs.dogbox.data.AppData
import pjs.dogbox.utils.CryptUtil
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.data.SharedData
import java.io.File
import java.io.FileOutputStream

class MediaDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID_IMAGE = "media_download_image_channel"
        private const val CHANNEL_ID_VIDEO = "media_download_video_channel"
        private const val CHANNEL_ID_MUSIC = "media_download_music_channel"

        private const val ACTION_START = "pjs.dogbox.action.MEDIA_DOWNLOAD_START"
        private const val ACTION_CANCEL = "pjs.dogbox.action.MEDIA_DOWNLOAD_CANCEL"
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_REFERER = "extra_referer"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DIR_NAME = "extra_dir_name"
        const val EXTRA_EXT = "extra_ext"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private const val NOTIFICATION_ID_PICTURES = 2001
        private const val NOTIFICATION_ID_VIDEOS = 2002
        private const val NOTIFICATION_ID_MUSIC = 2003

        /** 系统下载器轮询间隔 */
        private const val SYSTEM_POLL_INTERVAL_MS = 800L

        /** 内置下载通知刷新最小间隔 */
        private const val BUILTIN_NOTIFY_INTERVAL_MS = 500L

        private fun notificationIdForDirName(dirName: String): Int = when (dirName) {
            "Pictures" -> NOTIFICATION_ID_PICTURES
            "Videos" -> NOTIFICATION_ID_VIDEOS
            "Music" -> NOTIFICATION_ID_MUSIC
            else -> NOTIFICATION_ID_PICTURES
        }

        private fun channelIdForDirName(dirName: String): String = when (dirName) {
            "Pictures" -> CHANNEL_ID_IMAGE
            "Videos" -> CHANNEL_ID_VIDEO
            "Music" -> CHANNEL_ID_MUSIC
            else -> CHANNEL_ID_IMAGE
        }

        fun typeNameForDirName(dirName: String): String = when (dirName) {
            "Pictures" -> "图片素材"
            "Videos" -> "视频素材"
            "Music" -> "背景音乐素材"
            else -> "素材"
        }

        fun start(
            context: Context,
            urls: List<String>,
            referer: String,
            title: String,
            dirName: String,
            ext: String,
        ) {
            val intent = Intent(context, MediaDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URLS, urls.toTypedArray())
                putExtra(EXTRA_REFERER, referer)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DIR_NAME, dirName)
                putExtra(EXTRA_EXT, ext)
                putExtra(EXTRA_NOTIFICATION_ID, notificationIdForDirName(dirName))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, notificationId: Int) {
            val intent = Intent(context, MediaDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<Int, Job>()

    /** notificationId -> 系统 DownloadManager 任务 id 列表 */
    private val systemDownloadIds = mutableMapOf<Int, List<Long>>()

    /** 内置 Ktor 下载客户端：大文件下载禁用整体超时，仅保留连接/读超时 */
    private val ktorClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = HttpUtil.okHttpClient
            }
            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val urls = intent.getStringArrayExtra(EXTRA_URLS)?.toList() ?: emptyList()
                if (urls.isEmpty()) {
                    stopIfIdle()
                    return START_NOT_STICKY
                }
                val referer = intent.getStringExtra(EXTRA_REFERER) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val dirName = intent.getStringExtra(EXTRA_DIR_NAME) ?: "Download"
                val ext = intent.getStringExtra(EXTRA_EXT) ?: "mp4"
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID_PICTURES)

                ensureChannel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        notificationId,
                        buildProgressNotification(
                            notificationId,
                            dirName,
                            urls.size,
                            0,
                            0,
                            0f,
                        ),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(
                        notificationId,
                        buildProgressNotification(
                            notificationId,
                            dirName,
                            urls.size,
                            0,
                            0,
                            0f,
                        ),
                    )
                }

                activeJobs[notificationId]?.cancel()
                activeJobs.remove(notificationId)

                val useSystemDownloader =
                    SharedData.getDownloadMode(this) == 1 &&
                        getSystemService(Context.DOWNLOAD_SERVICE) is DownloadManager

                val job = if (useSystemDownloader) {
                    launchSystemDownloadBatch(notificationId, urls, referer, title, dirName, ext)
                } else {
                    launchBuiltInDownloadBatch(notificationId, urls, referer, title, dirName, ext)
                }
                activeJobs[notificationId] = job
            }

            ACTION_CANCEL -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID_PICTURES)
                activeJobs.remove(notificationId)?.cancel()

                // 取消系统下载器中的任务（同时删除已下载的部分文件）
                systemDownloadIds.remove(notificationId)?.let { ids ->
                    runCatching {
                        getSystemService(DownloadManager::class.java)?.remove(*ids.toLongArray())
                    }
                }

                getSystemService(NotificationManager::class.java)?.cancel(notificationId)
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        runCatching { ktorClient.close() }
        super.onDestroy()
    }

    // ==================== 内置下载器（Ktor） ====================

    private fun launchBuiltInDownloadBatch(
        notificationId: Int,
        urls: List<String>,
        referer: String,
        title: String,
        dirName: String,
        ext: String,
    ): Job = scope.launch {
        var ok = 0
        var fail = 0
        for ((i, url) in urls.withIndex()) {
            if (!isActive) return@launch
            val success = downloadWithKtor(
                url,
                referer,
                title,
                dirName,
                i + 1,
                ext,
            ) { fileFraction ->
                val overall = (ok + fail + fileFraction) / urls.size
                notifyProgress(notificationId, dirName, urls.size, ok, fail, overall)
            }
            if (success) ok++ else fail++
            if (!isActive) return@launch
            notifyProgress(notificationId, dirName, urls.size, ok, fail, (ok + fail) / urls.size.toFloat())
        }
        if (!isActive) return@launch
        notifyCompletion(notificationId, dirName, urls.size, ok, fail)
        activeJobs.remove(notificationId)
        stopIfIdle()
    }

    /**
     * 使用内置 Ktor 客户端流式下载单个文件
     * @param onProgress 当前文件下载进度 0f..1f（未知大小时为 0f）
     */
    private suspend fun downloadWithKtor(
        url: String,
        referer: String,
        title: String,
        dirName: String,
        index: Int,
        ext: String,
        onProgress: suspend (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "DogBox/Material/${CryptUtil.md5(title, 16)}/$dirName",
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${index}_${System.currentTimeMillis()}.$ext")

        try {
            val success = ktorClient.prepareGet(url) {
                header("Referer", referer)
                header("User-Agent", AppData.UA_CHROME_M)
            }.execute { response ->
                if (!response.status.isSuccess()) return@execute false
                val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                val channel = response.bodyAsChannel()
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastNotifyAt = System.currentTimeMillis()
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read <= 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyAt >= BUILTIN_NOTIFY_INTERVAL_MS) {
                            lastNotifyAt = now
                            onProgress(
                                if (total > 0) {
                                    (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
                                } else {
                                    0f
                                },
                            )
                        }
                    }
                    output.flush()
                    downloaded > 0 && (total <= 0 || downloaded >= total)
                }
            }

            if (success) {
                MediaScannerConnection.scanFile(
                    this@MediaDownloadService,
                    arrayOf(file.absolutePath),
                    null,
                    null,
                )
            } else {
                runCatching { file.delete() }
            }
            success
        } catch (e: CancellationException) {
            runCatching { file.delete() }
            throw e
        } catch (_: Exception) {
            runCatching { file.delete() }
            false
        }
    }

    // ==================== 系统下载器（DownloadManager） ====================

    private fun launchSystemDownloadBatch(
        notificationId: Int,
        urls: List<String>,
        referer: String,
        title: String,
        dirName: String,
        ext: String,
    ): Job {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val subDir = "DogBox/Material/${CryptUtil.md5(title, 16)}/$dirName"

        val enqueuedIds = mutableListOf<Long>()
        for ((i, url) in urls.withIndex()) {
            val fileName = "${i + 1}_${System.currentTimeMillis()}.$ext"
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription(title.ifBlank { typeNameForDirName(dirName) })
                    addRequestHeader("Referer", referer)
                    addRequestHeader("User-Agent", AppData.UA_CHROME_M)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$subDir/$fileName")
                }
                enqueuedIds += dm.enqueue(request)
            } catch (_: Exception) {
                enqueuedIds += -1L
            }
        }
        systemDownloadIds[notificationId] = enqueuedIds

        return scope.launch {
            val pending = enqueuedIds.filter { it != -1L }.toMutableSet()
            var ok = 0
            var fail = enqueuedIds.count { it == -1L }

            fun bytesFraction(): Float {
                if (pending.isEmpty()) return 1f
                val query = DownloadManager.Query().setFilterById(*pending.toLongArray())
                var soFar = 0L
                var totalSize = 0L
                dm.query(query)?.use { cursor ->
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    while (cursor.moveToNext()) {
                        if (bytesIdx >= 0) soFar += cursor.getLong(bytesIdx).coerceAtLeast(0L)
                        if (totalIdx >= 0) cursor.getLong(totalIdx).takeIf { it > 0 }?.let { totalSize += it }
                    }
                }
                return if (totalSize > 0) (soFar.toDouble() / totalSize).coerceIn(0.0, 1.0).toFloat() else 0f
            }

            while (isActive && pending.isNotEmpty()) {
                delay(SYSTEM_POLL_INTERVAL_MS)
                if (!isActive) break

                val query = DownloadManager.Query().setFilterById(*pending.toLongArray())
                dm.query(query)?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                    val statusIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        when (cursor.getInt(statusIdx)) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                pending.remove(id)
                                ok++
                                val localUri = if (localUriIdx >= 0) cursor.getString(localUriIdx) else null
                                localUri?.let {
                                    runCatching { Uri.parse(it).path }.getOrNull()?.let { path ->
                                        MediaScannerConnection.scanFile(
                                            this@MediaDownloadService,
                                            arrayOf(path),
                                            null,
                                            null,
                                        )
                                    }
                                }
                            }

                            DownloadManager.STATUS_FAILED -> {
                                pending.remove(id)
                                fail++
                            }
                        }
                    }
                }
                if (!isActive) return@launch
                val overall = (ok + fail + bytesFraction()) / urls.size
                notifyProgress(notificationId, dirName, urls.size, ok, fail, overall.toFloat())
            }

            if (!isActive) return@launch
            notifyCompletion(notificationId, dirName, urls.size, ok, fail)
            activeJobs.remove(notificationId)
            systemDownloadIds.remove(notificationId)
            stopIfIdle()
        }
    }

    // ==================== 通知 ====================

    private fun notifyProgress(
        notificationId: Int,
        dirName: String,
        total: Int,
        ok: Int,
        fail: Int,
        overallFraction: Float,
    ) {
        getSystemService(NotificationManager::class.java)?.notify(
            notificationId,
            buildProgressNotification(notificationId, dirName, total, ok, fail, overallFraction),
        )
    }

    private fun buildProgressNotification(
        notificationId: Int,
        dirName: String,
        totalCount: Int,
        ok: Int,
        fail: Int,
        fraction: Float,
    ): Notification {
        val percent = (fraction.coerceIn(0f, 1f) * 100).toInt()
        return NotificationCompat.Builder(this, channelIdForDirName(dirName))
            .setSmallIcon(R.drawable.download_outlined)
            .setContentTitle("${typeNameForDirName(dirName)}下载中 $percent%")
            .setContentText("共 $totalCount 个，成功 $ok 个，失败 $fail 个")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .addAction(0, "取消", buildCancelPendingIntent(notificationId))
            .build()
    }

    private fun buildCancelPendingIntent(notificationId: Int): PendingIntent = PendingIntent.getService(
        this,
        notificationId,
        Intent(this, MediaDownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notifyCompletion(
        notificationId: Int,
        dirName: String,
        total: Int,
        ok: Int,
        fail: Int,
    ) {
        val openIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification =
            NotificationCompat.Builder(this, channelIdForDirName(dirName))
                .setSmallIcon(R.drawable.download_outlined)
                .setContentTitle("${typeNameForDirName(dirName)}下载完成")
                .setContentText("共 $total 个，成功 $ok 个，失败 $fail 个")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("共 $total 个，成功 $ok 个，失败 $fail 个"),
                )
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java)?.notify(notificationId, notification)
    }

    private fun stopIfIdle() {
        if (activeJobs.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_IMAGE,
                    "图片素材下载",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "显示图片素材下载进度"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_VIDEO,
                    "视频素材下载",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "显示视频素材下载进度"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_MUSIC,
                    "背景音乐下载",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "显示背景音乐下载进度"
                    setShowBadge(false)
                }
            )
        }
    }
}
