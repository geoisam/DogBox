package pjs.dogbox.services

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
import androidx.core.content.edit
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
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.data.repository.AppRepository
import pjs.dogbox.utils.network.HttpUtil
import pjs.dogbox.utils.security.CryptUtil
import java.io.File
import java.io.FileOutputStream

class MediaDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "Default"

        /** 旧版本遗留的通知渠道，创建新渠道时一并删除 */
        private val LEGACY_CHANNEL_IDS = listOf(
            "media_download_image_channel",
            "media_download_video_channel",
            "media_download_music_channel",
        )

        private const val ACTION_START = "pjs.dogbox.action.MEDIA_DOWNLOAD_START"
        private const val ACTION_CANCEL = "pjs.dogbox.action.MEDIA_DOWNLOAD_CANCEL"
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_REFERER = "extra_referer"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DIR_NAME = "extra_dir_name"
        const val EXTRA_EXT = "extra_ext"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_NOTIFY_TITLE = "extra_notify_title"

        /** 系统下载器轮询间隔 */
        private const val SYSTEM_POLL_INTERVAL_MS = 800L

        private const val PREFS_NAME = "app_settings"
        private const val KEY_NEXT_NOTIFICATION_ID = "next_notification_id"
        private const val NOTIFICATION_ID_START = 10001
        private const val NOTIFICATION_ID_END = 999999

        /**
         * 生成全局唯一的通知 ID，持久化自增，避免 Service 重建导致 ID 重用而覆盖历史完成通知。
         * 类似 QQ/微信/抖音的聊天通知，每条完成通知独立存在、互不覆盖。
         */
        fun obtainNextNotificationId(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var cur = prefs.getInt(KEY_NEXT_NOTIFICATION_ID, NOTIFICATION_ID_START)
            if (cur < NOTIFICATION_ID_START || cur >= NOTIFICATION_ID_END) cur =
                NOTIFICATION_ID_START
            val next = cur + 1
            prefs.edit { putInt(KEY_NEXT_NOTIFICATION_ID, next) }
            // 叠加时间戳低位 + 随机数，极端情况下（卸载重装等）也能降低碰撞概率
            // 但主要唯一性由持久化自增值保证
            return cur
        }

        fun typeNameForDirName(dirName: String): String = when (dirName) {
            "Pictures" -> "图片素材"
            "Videos" -> "视频素材"
            "Music" -> "背景音乐素材"
            else -> "素材"
        }

        private fun defaultDoneTitleForDirName(dirName: String): String = when (dirName) {
            "Pictures" -> "图片下载完成"
            "Videos" -> "视频下载完成"
            "Music" -> "音频下载完成"
            else -> "下载完成"
        }

        fun start(
            context: Context,
            urls: List<String>,
            referer: String,
            title: String,
            dirName: String,
            ext: String,
            notifyTitle: String = "",
        ) {
            val intent = Intent(context, MediaDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URLS, urls.toTypedArray())
                putExtra(EXTRA_REFERER, referer)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DIR_NAME, dirName)
                putExtra(EXTRA_EXT, ext)
                putExtra(EXTRA_NOTIFY_TITLE, notifyTitle)
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

    /** 独立下载 OkHttp：不与 Coil/解析共用 Dispatcher，避免大文件与图片网格抢占 maxRequestsPerHost */
    private val downloadOkHttpClient: okhttp3.OkHttpClient by lazy {
        HttpUtil.okHttpClient.newBuilder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 内置 Ktor 下载客户端：大文件下载禁用整体超时，读空闲 120s 适配 30-300MB 视频弱网 */
    private val ktorClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = downloadOkHttpClient
            }
            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 120_000
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
                val doneTitle = intent.getStringExtra(EXTRA_NOTIFY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultDoneTitleForDirName(dirName)
                // 每批下载使用全局唯一通知 id（持久化自增，从 10001 开始），避免覆盖上一条完成通知
                // 修复：之前使用 Service 实例内的 AtomicInteger，Service 销毁重建后会重置，导致新通知覆盖旧通知
                // 现在改为 SharedPreferences 持久化，保证像 QQ/微信/抖音那样追加而不是替代
                val notificationId = obtainNextNotificationId(this)

                ensureChannel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        notificationId,
                        buildForegroundNotification(notificationId),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(
                        notificationId,
                        buildForegroundNotification(notificationId),
                    )
                }

                activeJobs[notificationId]?.cancel()
                activeJobs.remove(notificationId)

                val useSystemDownloader =
                    AppRepository.getDownloadMode(this) == 1 &&
                            getSystemService(Context.DOWNLOAD_SERVICE) is DownloadManager

                val job = if (useSystemDownloader) {
                    launchSystemDownloadBatch(
                        notificationId,
                        urls,
                        referer,
                        title,
                        dirName,
                        ext,
                        doneTitle
                    )
                } else {
                    launchBuiltInDownloadBatch(
                        notificationId,
                        urls,
                        referer,
                        title,
                        dirName,
                        ext,
                        doneTitle
                    )
                }
                activeJobs[notificationId] = job
            }

            ACTION_CANCEL -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notificationId != -1) {
                    activeJobs.remove(notificationId)?.cancel()

                    // 取消系统下载器中的任务（同时删除已下载的部分文件）
                    systemDownloadIds.remove(notificationId)?.let { ids ->
                        runCatching {
                            getSystemService(DownloadManager::class.java)?.remove(*ids.toLongArray())
                        }
                    }

                    getSystemService(NotificationManager::class.java)?.cancel(notificationId)
                }
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
        doneTitle: String,
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
            )
            if (success) ok++ else fail++
        }
        if (!isActive) return@launch
        notifyCompletion(notificationId, doneTitle, urls.size, ok, fail)
        activeJobs.remove(notificationId)
        stopIfIdle()
    }

    /**
     * 使用内置 Ktor 客户端流式下载单个文件（大文件优化）
     * 按需求：不带 Referer，UA 固定 UA_CHROME_M；大文件 30-300MB 需重试+ .tmp 原子重命名，避免单次抖动导致失败
     */
    private suspend fun downloadWithKtor(
        url: String,
        referer: String,
        title: String,
        dirName: String,
        index: Int,
        ext: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "DogBox/Material/${CryptUtil.md5(title)}/$dirName",
        )
        if (!dir.exists()) dir.mkdirs()
        val finalFile = File(dir, "${index}_${System.currentTimeMillis()}.$ext")
        val tmpFile = File(dir, "${finalFile.name}.tmp")

        suspend fun tryOnce(): Boolean {
            return try {
                ktorClient.prepareGet(url) {
                    header("User-Agent", AppConfig.UA_CHROME_M)
                }.execute { response ->
                    if (!response.status.isSuccess()) return@execute false
                    val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                    val channel = response.bodyAsChannel()
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read == -1) break
                            if (read <= 0) continue
                            output.write(buffer, 0, read)
                            downloaded += read
                        }
                        output.flush()
                        downloaded > 0 && (total <= 0 || downloaded >= total)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

        try {
            var success = false
            repeat(3) { attempt ->
                if (tmpFile.exists()) runCatching { tmpFile.delete() }
                success = tryOnce()
                if (success) {
                    runCatching { tmpFile.renameTo(finalFile) }
                    // rename 可能在跨分区失败，兜底拷贝
                    if (!finalFile.exists() && tmpFile.exists()) {
                        runCatching {
                            tmpFile.copyTo(
                                finalFile,
                                overwrite = true
                            ); tmpFile.delete()
                        }
                    }
                    return@withContext true.also {
                        MediaScannerConnection.scanFile(
                            this@MediaDownloadService,
                            arrayOf(finalFile.absolutePath),
                            null,
                            null,
                        )
                    }
                }
                if (attempt < 2) delay(800L * (attempt + 1))
            }
            runCatching { tmpFile.delete() }
            runCatching { finalFile.delete() }
            false
        } catch (e: CancellationException) {
            runCatching { tmpFile.delete() }
            runCatching { finalFile.delete() }
            throw e
        } catch (_: Exception) {
            runCatching { tmpFile.delete() }
            runCatching { finalFile.delete() }
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
        doneTitle: String,
    ): Job {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val subDir = "DogBox/Material/${CryptUtil.md5(title)}/$dirName"

        val enqueuedIds = mutableListOf<Long>()
        for ((i, url) in urls.withIndex()) {
            val fileName = "${i + 1}_${System.currentTimeMillis()}.$ext"
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription(title.ifBlank { typeNameForDirName(dirName) })
                    addRequestHeader("User-Agent", AppConfig.UA_CHROME_M)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "$subDir/$fileName"
                    )
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
                                val localUri =
                                    if (localUriIdx >= 0) cursor.getString(localUriIdx) else null
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
            }

            if (!isActive) return@launch
            notifyCompletion(notificationId, doneTitle, urls.size, ok, fail)
            activeJobs.remove(notificationId)
            systemDownloadIds.remove(notificationId)
            stopIfIdle()
        }
    }

    // ==================== 通知 ====================

    /** 前台服务必需的占位通知，不展示进度，仅保留取消入口 */
    private fun buildForegroundNotification(notificationId: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_outlined)
            .setContentTitle("正在下载")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "取消", buildCancelPendingIntent(notificationId))
            .build()

    private fun buildCancelPendingIntent(notificationId: Int): PendingIntent =
        PendingIntent.getService(
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
        doneTitle: String,
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
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.download_outlined)
                .setContentTitle(doneTitle)
                .setContentText("共 $total 个，成功 $ok 个，失败 $fail 个")
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

        getSystemService(NotificationManager::class.java)?.notify(notificationId, notification)
    }

    private fun stopIfIdle() {
        if (activeJobs.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        LEGACY_CHANNEL_IDS.forEach { id ->
            runCatching { nm.deleteNotificationChannel(id) }
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }
}
