package pjs.dogbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import pjs.dogbox.MainActivity
import pjs.dogbox.R
import pjs.dogbox.utils.AppData
import pjs.dogbox.utils.CryptUtil
import pjs.dogbox.utils.HttpUtil
import java.io.File
import java.io.FileOutputStream

class MediaDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "media_download_channel"
        const val NOTIFICATION_ID = 2001
        private const val ACTION_START = "pjs.dogbox.action.MEDIA_DOWNLOAD_START"
        private const val ACTION_CANCEL = "pjs.dogbox.action.MEDIA_DOWNLOAD_CANCEL"
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_REFERER = "extra_referer"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DIR_NAME = "extra_dir_name"
        const val EXTRA_EXT = "extra_ext"

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
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val urls = intent.getStringArrayExtra(EXTRA_URLS)?.toList() ?: emptyList()
                if (urls.isEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val referer = intent.getStringExtra(EXTRA_REFERER) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val dirName = intent.getStringExtra(EXTRA_DIR_NAME) ?: "Download"
                val ext = intent.getStringExtra(EXTRA_EXT) ?: "mp4"
                ensureChannel()
                val notification = buildNotification("准备下载", 0, 0, 0, urls.size, title)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                downloadJob?.cancel()
                downloadJob = scope.launch {
                    var ok = 0
                    for ((i, url) in urls.withIndex()) {
                        if (!isActive) break
                        val success = downloadFile(
                            url,
                            referer,
                            title,
                            dirName,
                            i + 1,
                            ext,
                        ) { done, total ->
                            getSystemService(NotificationManager::class.java)?.notify(
                                NOTIFICATION_ID,
                                buildNotification(
                                    "下载中 ${i + 1}/${urls.size}",
                                    done,
                                    total,
                                    i,
                                    urls.size,
                                    title,
                                ),
                            )
                        }
                        if (success) ok++
                    }
                    getSystemService(NotificationManager::class.java)?.notify(
                        NOTIFICATION_ID,
                        buildCompletionNotification(ok, urls.size, title),
                    )
                    ServiceCompat.stopForeground(this@MediaDownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
            ACTION_CANCEL -> {
                downloadJob?.cancel()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "素材下载",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示素材下载进度"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        text: String,
        done: Long,
        total: Long,
        doneCount: Int,
        totalCount: Int,
        title: String,
    ): Notification {
        val contentText = if (total > 0) {
            val pct = (done * 100 / total).coerceIn(0, 100)
            "$text  ${formatBytes(done)}/${formatBytes(total)}  $pct%"
        } else {
            "$text  ${formatBytes(done)}"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_outlined)
            .setContentTitle(if (title.isBlank()) "素材下载" else title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                100,
                if (total > 0) (done * 100 / total).toInt() else 0,
                total <= 0,
            )
            .addAction(
                0,
                "取消",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, MediaDownloadService::class.java).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.2fGB", bytes / gb)
            bytes >= mb -> String.format("%.2fMB", bytes / mb)
            bytes >= kb -> String.format("%.0fKB", bytes / kb)
            else -> "${bytes}B"
        }
    }

    private fun buildCompletionNotification(ok: Int, total: Int, title: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_outlined)
            .setContentTitle("素材下载完成")
            .setContentText("已保存 $ok/$total 个")
            .setAutoCancel(true)
            .build()

    private suspend fun downloadFile(
        url: String,
        referer: String,
        title: String,
        dirName: String,
        index: Int,
        ext: String,
        onProgress: (Long, Long) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .header("User-Agent", AppData.UA_CHROME_M)
                .build()
            HttpUtil.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                val total = body.contentLength()
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DogBox/Material/${CryptUtil.md5(title, 16)}/$dirName"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "${index}_${System.currentTimeMillis()}.$ext")
                val input = body.byteStream()
                val output = FileOutputStream(file)
                var downloaded = 0L
                val buffer = ByteArray(8192)
                var lastProgressAt = System.currentTimeMillis()
                try {
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (downloaded % (256 * 1024) == 0L ||
                            downloaded == total ||
                            now - lastProgressAt >= 500
                        ) {
                            lastProgressAt = now
                            onProgress(downloaded, total)
                        }
                    }
                } finally {
                    runCatching { output.flush() }
                    runCatching { output.close() }
                    runCatching { input.close() }
                }
                if (isActive) {
                    onProgress(downloaded, total)
                    true
                } else {
                    file.delete()
                    false
                }
            }
        }.getOrDefault(false)
    }
}