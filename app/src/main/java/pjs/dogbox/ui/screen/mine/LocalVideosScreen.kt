package pjs.dogbox.ui.screen.mine

import pjs.dogbox.data.localvideo.formatDuration
import pjs.dogbox.data.localvideo.formatSize
import pjs.dogbox.data.localvideo.hasFileAccessPermission
import pjs.dogbox.data.localvideo.openFileAccessPermissionPage

import pjs.dogbox.data.localvideo.LocalVideoItem
import pjs.dogbox.data.localvideo.LocalVideoStore
import pjs.dogbox.data.localvideo.queryMediaStoreVideos

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import androidx.compose.material3.CircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalVideosScreen(
    onBack: () -> Unit = {},
    onPlayVideo: (uri: String, title: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasFileAccessPermission(context)) }
    var videos by remember { mutableStateOf(LocalVideoStore.getScannedVideos(context)) }
    var isRescanning by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var rescanKey by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<String>() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun refreshVideos(showSpinner: Boolean) {
        scope.launch {
            if (showSpinner) isRefreshing = true
            val result = withContext(Dispatchers.IO) { queryMediaStoreVideos(context) }
            LocalVideoStore.saveScannedVideos(context, result)
            LocalVideoStore.markScanned(context)
            videos = result
            if (showSpinner) isRefreshing = false
        }
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasFileAccessPermission(context)
                videos = LocalVideoStore.getScannedVideos(context)
                if (hasPermission) rescanKey++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(rescanKey, hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        isRescanning = true
        val result = withContext(Dispatchers.IO) { queryMediaStoreVideos(context) }
        LocalVideoStore.saveScannedVideos(context, result)
        LocalVideoStore.markScanned(context)
        videos = result
        isRescanning = false
    }

    BackHandler(enabled = isSelectMode) {
        isSelectMode = false
        selectedUris.clear()
    }

    fun confirmDelete() {
        val targets = selectedUris.toSet()
        if (targets.isEmpty()) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                var ok = true
                targets.forEach { uriString ->
                    if (!deleteVideoFile(context, uriString)) ok = false
                }
                ok
            }
            val remaining = videos.filter { it.uri.toString() !in targets }
            LocalVideoStore.saveScannedVideos(context, remaining)
            videos = remaining
            selectedUris.clear()
            isSelectMode = false
            Toast.makeText(context, if (result) "已删除" else "删除失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("本地视频", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    if (videos.isNotEmpty()) {
                        FilledTonalIconButton(
                            onClick = {
                                if (isSelectMode) {
                                    isSelectMode = false
                                    selectedUris.clear()
                                } else {
                                    isSelectMode = true
                                }
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isSelectMode) R.drawable.close_outlined
                                    else R.drawable.delete_outlined
                                ),
                                contentDescription = if (isSelectMode) "取消" else "删除",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isSelectMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                selectedUris.clear()
                                selectedUris.addAll(videos.map { it.uri.toString() })
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("全选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                val current = selectedUris.toSet()
                                selectedUris.clear()
                                videos.map { it.uri.toString() }
                                    .filter { it !in current }
                                    .forEach(selectedUris::add)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("反选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                isSelectMode = false
                                selectedUris.clear()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("取消")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedUris.isNotEmpty(),
                    ) {
                        Text("删除（${selectedUris.size}）")
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                !hasPermission -> {
                    CenterPillButton(
                        text = "去授权",
                        onClick = { openFileAccessPermissionPage(context) },
                    )
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (videos.isEmpty() && isRescanning) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(56.dp))
                            }
                        } else {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { refreshVideos(showSpinner = true) },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (videos.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "未扫描到视频",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        state = gridState,
                                        contentPadding = PaddingValues(
                                            start = 8.dp,
                                            top = 8.dp,
                                            end = 8.dp,
                                            bottom = 32.dp,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(videos, key = { it.uri.toString() }) { item ->
                                            LocalVideoGridItem(
                                                item = item,
                                                isSelectMode = isSelectMode,
                                                isSelected = item.uri.toString() in selectedUris,
                                                onSelect = {
                                                    val key = item.uri.toString()
                                                    if (selectedUris.contains(key)) selectedUris.remove(key)
                                                    else selectedUris.add(key)
                                                },
                                                onLongClick = {
                                                    if (!isSelectMode) {
                                                        isSelectMode = true
                                                        selectedUris.clear()
                                                        selectedUris.add(item.uri.toString())
                                                    }
                                                },
                                                onClick = {
                                                    onPlayVideo(
                                                        item.uri.toString(),
                                                        item.displayName,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val isAtTop by remember {
                            derivedStateOf {
                                gridState.firstVisibleItemIndex == 0 &&
                                        gridState.firstVisibleItemScrollOffset < 50
                            }
                        }
                        var lastKnownOffset by remember { mutableIntStateOf(0) }
                        var isScrollingDown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            snapshotFlow {
                                gridState.isScrollInProgress to
                                        (gridState.firstVisibleItemIndex to
                                                gridState.firstVisibleItemScrollOffset)
                            }.collect { (isScrolling, state) ->
                                if (isScrolling) {
                                    val (index, offset) = state
                                    val currentTotal = index * 10000 + offset
                                    isScrollingDown = currentTotal > lastKnownOffset
                                    lastKnownOffset = currentTotal
                                }
                            }
                        }
                        val showFab by remember {
                            derivedStateOf { !isAtTop && !isScrollingDown }
                        }
                        if (showFab) {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        gridState.animateScrollToItem(0)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(24.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_upward_outlined),
                                    contentDescription = "回到顶部",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除", fontWeight = FontWeight.Bold) },
            text = { Text("确认删除这${selectedUris.size}项内容？删除后将无法查看。") },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    confirmDelete()
                }) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CenterPillButton(
    text: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.height(48.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalVideoGridItem(
    item: LocalVideoItem,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (isSelectMode) {
                    Modifier.clickable { onSelect() }
                } else {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Box {
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    VideoThumbnail(
                        uri = item.uri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                )
                            ),
                    )
                    if (item.duration > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = formatDuration(item.duration),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = item.displayName.substringBeforeLast('.'),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (item.width > 0 && item.height > 0) "${item.width}x${item.height}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatSize(item.size).takeIf { it.isNotEmpty() } ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isSelectMode) {
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
}

private fun deleteVideoFile(context: android.content.Context, uriString: String): Boolean {
    return try {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> File(uri.path ?: return false).delete()
            "content" -> context.contentResolver.delete(uri, null, null) > 0
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun VideoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.scheme == "file") {
                    val mmr = MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(uri.path ?: return@runCatching null)
                        mmr.getFrameAtTime(
                            1_000_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        )
                    } finally {
                        runCatching { mmr.release() }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(
                        uri,
                        android.util.Size(512, 512),
                        null,
                    )
                } else {
                    val id = uri.lastPathSegment?.toLongOrNull() ?: 0L
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null,
                    )
                }
            }.getOrNull()
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
}