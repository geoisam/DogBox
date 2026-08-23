package pjs.dogbox.ui.screen.discover

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.service.MediaDownloadService
import pjs.dogbox.data.AppData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAnalyzeResultScreen(
    type: String,
    urls: List<String>,
    referer: String,
    resultTitle: String,
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isImage = type == "image"
    val dirName = if (isImage) "Pictures" else "Videos"
    var isMultiSelect by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }

    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingDownload?.invoke()
        pendingDownload = null
    }

    fun ensureNotificationPermission(onGranted: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
            return true
        }
        pendingDownload = onGranted
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return false
    }

    fun startDownload(targets: List<Int>) {
        if (targets.isEmpty()) return
        if (!ensureFileAccess(context)) return
        val targetUrls = targets.map { urls[it] }
        ensureNotificationPermission {
            MediaDownloadService.start(
                context,
                targetUrls,
                referer,
                resultTitle,
                dirName,
                if (isImage) "png" else "mp4",
            )
            Toast.makeText(
                context,
                "开始下载 ${targetUrls.size} 个，请查看通知栏进度",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    BackHandler(enabled = isMultiSelect) {
        isMultiSelect = false
        selected.clear()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("解析结果", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            if (isMultiSelect) {
                                isMultiSelect = false
                                selected.clear()
                            } else {
                                isMultiSelect = true
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isMultiSelect) R.drawable.close_outlined
                                else R.drawable.select_all_outlined
                            ),
                            contentDescription = if (isMultiSelect) "取消" else "多选",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (isMultiSelect) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                selected.clear()
                                selected.addAll(urls.indices)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("全选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                val current = selected.toSet()
                                selected.clear()
                                urls.indices.filter { it !in current }.forEach(selected::add)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("反选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                isMultiSelect = false
                                selected.clear()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("取消")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { startDownload(selected.toList()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text("下载（${selected.size}）")
                    }
                } else {
                    Button(
                        onClick = { startDownload(urls.indices.toList()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("下载全部")
                    }
                }
            }
        },
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            items(count = urls.size) { index ->
                MediaCell(
                    index = index,
                    url = urls[index],
                    isImage = isImage,
                    shareLink = referer,
                    resultTitle = resultTitle,
                    isMultiSelect = isMultiSelect,
                    isSelected = index in selected,
                    onToggle = {
                        if (index in selected) selected.remove(index)
                        else selected.add(index)
                    },
                    onOpen = { onOpen(index) },
                    onEnterMultiSelect = {
                        isMultiSelect = true
                        selected.clear()
                        selected.add(index)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    index: Int,
    url: String,
    isImage: Boolean,
    shareLink: String,
    resultTitle: String,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onEnterMultiSelect: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (isMultiSelect) {
                    Modifier.clickable { onToggle() }
                } else {
                    Modifier.combinedClickable(
                        onClick = onOpen,
                        onLongClick = onEnterMultiSelect,
                    )
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.medium
                    )
                } else Modifier
            ),
    ) {
        if (isImage) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Referer", shareLink)
                            .set("User-Agent", AppData.UA_WECHAT_PC)
                            .build()
                    )
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { MediaCellPlaceholder(Modifier.fillMaxSize()) },
                error = { MediaCellPlaceholder(Modifier.fillMaxSize()) },
            )
        } else {
            NetworkVideoThumbnail(
                url = url,
                referer = shareLink,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (isImage) R.drawable.image_outlined else R.drawable.play_filled),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (isMultiSelect) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(R.drawable.check_outlined),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkVideoThumbnail(
    url: String,
    referer: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        val cached = VideoFrameCache.get(url)
        if (cached != null) {
            value = cached
        } else {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    try {
                        val headers = HashMap<String, String>().apply {
                            if (referer.isNotBlank()) put("Referer", referer)
                            put("User-Agent", AppData.UA_WECHAT_PC)
                        }
                        mmr.setDataSource(url, headers)
                        mmr.getFrameAtTime(
                            1_000_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        )
                    } finally {
                        runCatching { mmr.release() }
                    }
                }.getOrNull()
            }.also { bmp -> if (bmp != null) VideoFrameCache.put(url, bmp) }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        MediaCellPlaceholder(modifier)
    }
}

@Composable
private fun MediaCellPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.error_med_outline),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private object VideoFrameCache {
    private val cache = LruCache<String, Bitmap>(64)

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }
}