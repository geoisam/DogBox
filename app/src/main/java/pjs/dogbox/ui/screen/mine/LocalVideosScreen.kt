package pjs.dogbox.ui.screen.mine

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.data.localvideo.LocalVideoItem
import pjs.dogbox.data.localvideo.LocalVideoStore
import pjs.dogbox.data.localvideo.formatDuration
import pjs.dogbox.data.localvideo.formatSize
import pjs.dogbox.data.localvideo.hasFileAccessPermission
import pjs.dogbox.data.localvideo.openFileAccessPermissionPage
import pjs.dogbox.data.localvideo.queryMediaStoreVideos
import pjs.dogbox.ui.components.MiuixLoading
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

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
    val pullToRefreshState = rememberPullToRefreshState()

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

    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = "本地视频",
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.arrow_back_outlined),
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    if (videos.isNotEmpty()) {
                        MiuixIconButton(
                            onClick = {
                                if (isSelectMode) {
                                    isSelectMode = false
                                    selectedUris.clear()
                                } else {
                                    isSelectMode = true
                                }
                            },
                        ) {
                            MiuixIcon(
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
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isSelectMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                selectedUris.clear()
                                selectedUris.addAll(videos.map { it.uri.toString() })
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText("全选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val current = selectedUris.toSet()
                                selectedUris.clear()
                                videos.map { it.uri.toString() }
                                    .filter { it !in current }
                                    .forEach(selectedUris::add)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText("反选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isSelectMode = false
                                selectedUris.clear()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText("取消")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedUris.isNotEmpty(),
                    ) {
                        MiuixText(
                            text = "删除（${selectedUris.size}）",
                            color = MiuixTheme.colorScheme.onError,
                        )
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
                            MiuixLoading(modifier = Modifier.fillMaxSize())
                        } else {
                            PullToRefresh(
                                isRefreshing = isRefreshing,
                                onRefresh = { refreshVideos(showSpinner = true) },
                                pullToRefreshState = pullToRefreshState,
                                refreshTexts = listOf(
                                    "下拉刷新",
                                    "释放刷新",
                                    "正在刷新...",
                                    "刷新成功"
                                ),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (videos.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MiuixText(
                                            text = "未扫描到视频",
                                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                                                    if (selectedUris.contains(key)) selectedUris.remove(
                                                        key
                                                    )
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
                                MiuixIcon(
                                    painter = painterResource(R.drawable.arrow_upward_outlined),
                                    contentDescription = "回到顶部",
                                    tint = MiuixTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = showDeleteDialog,
        title = "删除",
        summary = "确认删除这${selectedUris.size}项内容？删除后将无法查看。",
        onDismissRequest = { showDeleteDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = "取消",
                onClick = { showDeleteDialog = false },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    showDeleteDialog = false
                    confirmDelete()
                },
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                ),
            ) {
                MiuixText(
                    text = "确认删除",
                    color = MiuixTheme.colorScheme.onError,
                )
            }
        }
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
            cornerRadius = 24.dp,
            modifier = Modifier.height(48.dp),
        ) {
            MiuixText(
                text = text,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

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
            .then(
                if (isSelected) {
                    Modifier.border(
                        2.dp,
                        MiuixTheme.colorScheme.primary,
                        RoundedCornerShape(16.dp)
                    )
                } else Modifier
            ),
        cornerRadius = 16.dp,
        onClick = {
            if (isSelectMode) onSelect() else onClick()
        },
        onLongPress = { onLongClick() },
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
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            MiuixText(
                                text = formatDuration(item.duration),
                                color = Color.White,
                                style = MiuixTheme.textStyles.footnote2,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    MiuixText(
                        text = item.displayName.substringBeforeLast('.'),
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MiuixText(
                            text = if (item.width > 0 && item.height > 0) "${item.width}x${item.height}" else "",
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        MiuixText(
                            text = formatSize(item.size).takeIf { it.isNotEmpty() } ?: "",
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                            if (isSelected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.check_outlined),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary,
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
                .background(MiuixTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            MiuixIcon(
                painter = painterResource(R.drawable.error_med_outline),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
            )
        }
    }
}
