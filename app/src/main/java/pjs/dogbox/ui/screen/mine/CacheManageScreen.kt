package pjs.dogbox.ui.screen.mine

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.ui.components.MiuixSubScreen
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private data class DeviceStorageInfo(
    val appUsedBytes: Long,
    val totalBytes: Long,
    val availableBytes: Long,
    val cacheBytes: Long,
)

@Composable
fun CacheManageScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var storageInfo by remember { mutableStateOf<DeviceStorageInfo?>(null) }
    var clearing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            storageInfo = withContext(Dispatchers.IO) { readStorageInfo(context) }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    MiuixSubScreen(
        title = "缓存管理",
        onBack = onBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StorageOverviewCard(storageInfo)

            CacheFileCard(
                cacheBytes = storageInfo?.cacheBytes,
                clearing = clearing,
                onClear = {
                    val cache = storageInfo?.cacheBytes ?: 0L
                    if (!clearing && cache > 0L) {
                        clearing = true
                        scope.launch {
                            withContext(Dispatchers.IO) { clearAppCache(context) }
                            storageInfo = withContext(Dispatchers.IO) { readStorageInfo(context) }
                            clearing = false
                            Toast.makeText(
                                context,
                                "已清理${formatFileSize(cache)}缓存",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
            )
        }
    }
}

private fun readStorageInfo(context: Context): DeviceStorageInfo {
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    val totalBytes = stat.totalBytes
    val availableBytes = stat.availableBytes

    var appUsedBytes = 0L
    try {
        val statsManager =
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val stats = statsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())
        appUsedBytes = stats.appBytes + stats.dataBytes
    } catch (_: Exception) {
        try {
            val sourceDir = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .applicationInfo?.sourceDir
            appUsedBytes = (sourceDir?.let { File(it).length() } ?: 0L) +
                    getDirSize(File(context.applicationInfo.dataDir))
        } catch (_: Exception) {
        }
    }

    val cacheBytes = getDirSize(context.cacheDir) + getDirSize(context.externalCacheDir)

    return DeviceStorageInfo(appUsedBytes, totalBytes, availableBytes, cacheBytes)
}

private fun clearAppCache(context: Context) {
    deleteDirContents(context.cacheDir)
    deleteDirContents(context.externalCacheDir)
}

private fun deleteDirContents(dir: File?) {
    if (dir == null || !dir.exists()) return
    dir.listFiles()?.forEach { it.deleteRecursively() }
}

private fun getDirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var size = 0L
    val files = dir.listFiles() ?: return 0L
    for (file in files) {
        size += if (file.isDirectory) getDirSize(file) else file.length()
    }
    return size
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.2f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.2f MB".format(mb)
    val gb = mb / 1024.0
    if (gb < 1024) return "%.2f GB".format(gb)
    return "%.2f TB".format(gb / 1024.0)
}

@Composable
private fun StorageOverviewCard(info: DeviceStorageInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val total = info?.totalBytes ?: 0L
            val appUsed = info?.appUsedBytes ?: 0L
            val available = info?.availableBytes ?: 0L
            val otherUsed = (total - available - appUsed).coerceAtLeast(0L)

            val appFraction = if (total > 0) appUsed.toFloat() / total else 0f
            val otherFraction = if (total > 0) otherUsed.toFloat() / total else 0f
            val availableFraction = if (total > 0) available.toFloat() / total else 0f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .weight(appFraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .background(MiuixTheme.colorScheme.primary),
                )
                Box(
                    modifier = Modifier
                        .weight(otherFraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .background(MiuixTheme.colorScheme.primaryContainer),
                )
                Box(
                    modifier = Modifier
                        .weight(availableFraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .background(MiuixTheme.colorScheme.dividerLine),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(
                    color = MiuixTheme.colorScheme.primary,
                    label = "应用已用空间",
                    modifier = Modifier.weight(1f),
                )
                LegendItem(
                    color = MiuixTheme.colorScheme.primaryContainer,
                    label = "其他已用空间",
                    modifier = Modifier.weight(1f),
                )
                LegendItem(
                    color = MiuixTheme.colorScheme.dividerLine,
                    label = "设备可用空间",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            MiuixText(
                text = "应用已用空间",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(4.dp))
            MiuixText(
                text = formatFileSize(appUsed),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            MiuixText(
                text = "占用设备约${
                    if (total > 0) "%.2f".format(appUsed * 100.0 / total) else "0.00"
                }%存储空间",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.size(6.dp))
        MiuixText(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
private fun CacheFileCard(
    cacheBytes: Long?,
    clearing: Boolean,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixText(
                    text = "缓存文件",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                TextButton(
                    text = if (clearing) "清理中…" else "清理",
                    onClick = onClear,
                    enabled = !clearing,
                )
            }
            Spacer(Modifier.height(6.dp))
            MiuixText(
                text = when {
                    cacheBytes == null -> "计算中…"
                    else -> formatFileSize(cacheBytes)
                },
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            MiuixText(
                text = "使用应用过程中产生的临时数据，清理后不影响正常使用",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}
