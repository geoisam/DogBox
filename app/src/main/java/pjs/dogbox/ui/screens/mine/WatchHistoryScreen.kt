package pjs.dogbox.ui.screens.mine

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import pjs.dogbox.R
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.data.repository.ApiSitesRepository
import pjs.dogbox.data.repository.AppRepository
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit = {},
    onPlayMovie: (AppRepository.WatchHistoryItem) -> Unit = {},
) {
    val context = LocalContext.current
    remember { ApiSitesRepository.ensureLoaded(context) }
    var items by remember { mutableStateOf(AppRepository.getWatchHistory(context)) }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    fun refresh() {
        items = AppRepository.getWatchHistory(context)
    }

    BackHandler(enabled = isDeleteMode) {
        isDeleteMode = false
        selectedIds.clear()
    }

    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = "观看历史",
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
                    if (isDeleteMode) {
                        MiuixIconButton(
                            onClick = {
                                isDeleteMode = false
                                selectedIds.clear()
                            },
                        ) {
                            MiuixIcon(
                                painter = painterResource(R.drawable.close_outlined),
                                contentDescription = "取消",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else if (items.isNotEmpty()) {
                        MiuixIconButton(onClick = { isDeleteMode = true }) {
                            MiuixIcon(
                                painter = painterResource(R.drawable.delete_outlined),
                                contentDescription = "删除",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isDeleteMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                val filtered =
                                    items.filter { it.orientation == pagerState.currentPage }
                                val allKeys = filtered.map { "${it.key}|${it.id}" }
                                selectedIds.clear()
                                selectedIds.addAll(allKeys)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText("全选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val filtered =
                                    items.filter { it.orientation == pagerState.currentPage }
                                val allKeys = filtered.map { "${it.key}|${it.id}" }
                                val currentSelected = selectedIds.toSet()
                                selectedIds.clear()
                                allKeys.filter { it !in currentSelected }
                                    .forEach { selectedIds.add(it) }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText("反选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isDeleteMode = false
                                selectedIds.clear()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText("取消")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                showDeleteDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        MiuixText(
                            text = "删除（${selectedIds.size}）",
                            color = MiuixTheme.colorScheme.onError,
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to "竖屏", 1 to "横屏").forEach { (value, label) ->
                    val selected = pagerState.currentPage == value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { scope.launch { pagerState.animateScrollToPage(value) } }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        MiuixText(
                            text = label,
                            color = if (selected) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.onSurfaceSecondary,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiuixIcon(
                            painter = painterResource(R.drawable.history_outlined),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.4f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MiuixText(
                            text = "暂无观看记录",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            style = MiuixTheme.textStyles.body1,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MiuixText(
                            text = "观看影视超过10秒将自动记录",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.6f),
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                ) { page ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items.filter { it.orientation == page },
                            key = { "${it.key}|${it.id}" }) { item ->
                            HistoryGridItem(
                                item = item,
                                isDeleteMode = isDeleteMode,
                                isSelected = selectedIds.contains("${item.key}|${item.id}"),
                                onSelect = {
                                    val key = "${item.key}|${item.id}"
                                    if (selectedIds.contains(key)) selectedIds.remove(key)
                                    else selectedIds.add(key)
                                },
                                onLongClick = {
                                    if (!isDeleteMode) {
                                        isDeleteMode = true
                                        selectedIds.clear()
                                        selectedIds.add("${item.key}|${item.id}")
                                    }
                                },
                                onClick = {
                                    onPlayMovie(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = showDeleteDialog,
        title = "删除",
        summary = "确认删除这${selectedIds.size}项内容？删除后将无法查看。",
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
                    AppRepository.removeWatchHistory(context, selectedIds.toSet())
                    selectedIds.clear()
                    isDeleteMode = false
                    refresh()
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
private fun HistoryGridItem(
    item: AppRepository.WatchHistoryItem,
    isDeleteMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val progressPercentText =
        if (item.duration > 0) "${item.progress * 100 / item.duration}%" else "0%"

    Card(
        modifier = Modifier.then(
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
            if (isDeleteMode) onSelect() else onClick()
        },
        onLongPress = { onLongClick() },
    ) {
        Box {
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.coverUrl)
                            .crossfade(true)
                            .httpHeaders(
                                NetworkHeaders.Builder()
                                    .set("Referer", item.host)
                                    .build()
                            ).build(),
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        MiuixText(
                            text = AppConfig.sourceName(item.key),
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(69.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = progressPercentText,
                            color = Color.White,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black
                                    )
                                )
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = item.episodeName,
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    MiuixText(
                        text = item.title,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MiuixText(
                        text = item.formattedDate,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }

            if (isDeleteMode) {
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
                            painter = painterResource(R.drawable.close_outlined),
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