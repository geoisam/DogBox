package pjs.dogbox.ui.screen.movie

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.ui.screen.PagerTabFollowEffect
import pjs.dogbox.utils.AppData
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.utils.SimpleDragHandle
import java.net.URLEncoder

private enum class SearchSource(
    val label: String,
    val host: String,
    val searchUrl: String,
) {
    YXXQ("映像星球", AppData.YXXQ_HOST, AppData.YXXQ_SEARCH),
    NIVOD("泥视频", AppData.NIVOD_HOST, AppData.NIVOD_SEARCH),
}

private data class SearchItem(
    val link: String,
    val cover: String,
    val title: String,
    val note: String,
    val year: String,
    val subtitle: String,
    val host: String,
)

private data class PageInfo(
    val hasNext: Boolean,
    val nextUrl: String?,
)

private fun checkPageInfo(doc: Document, currentPage: Int): PageInfo {
    var lastPage = doc.select("#page a[title=尾页]").firstOrNull()?.attr("href")?.let { href ->
        Regex("""(\d+)\D*$""").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
    } ?: 0
    if (lastPage <= 0) {
        for (a in doc.select("#page a.page-number")) {
            val num = a.text().trim().toIntOrNull() ?: continue
            if (num > lastPage) lastPage = num
        }
    }
    return PageInfo(hasNext = currentPage < lastPage, nextUrl = null)
}

private fun parseSearchResults(
    html: String,
    host: String,
    currentPage: Int = 1,
): Pair<List<SearchItem>, PageInfo> {
    val doc = Ksoup.parse(html)
    val cardElements = doc.select("div.module-card-item").filterNot { it.hasClass("mac-ad-card") }
    val items = cardElements.mapNotNull { card ->
        val linkEl = card.select("a.play-btn").first() ?: return@mapNotNull null
        val imgEl = card.select("img[data-original]").first() ?: return@mapNotNull null
        val titleEl = card.select("div.module-card-item-title").first() ?: return@mapNotNull null

        val linkText = linkEl.attr("href")
        val link = if (linkText.startsWith("http")) linkText else "$host$linkText"
        val coverText = imgEl.attr("data-original")
        val cover = if (coverText.startsWith("http")) coverText else "$host$coverText"
        val title = titleEl.text().trim()
        val note = card.select("div.module-item-note").firstOrNull()?.text()?.trim() ?: ""

        val infoText =
            card.select("div.module-info-item-content:has(span.slash)").firstOrNull()?.text()
                ?.trim()?.replace(" ", "") ?: ""

        val year = infoText.split("/").firstOrNull()?.trim() ?: ""
        val subtitle = infoText.split("/").drop(1).joinToString("/").trim().replace(",", "/")

        SearchItem(
            link = link,
            cover = cover,
            title = title,
            note = note,
            year = year,
            subtitle = subtitle,
            host = host,
        )
    }
    val pageInfo = checkPageInfo(doc, currentPage)
    return items to pageInfo
}

private suspend fun fetchPage(
    source: SearchSource,
    keyword: String,
    page: Int,
): Result<Pair<List<SearchItem>, PageInfo>> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(keyword, "UTF-8")
    val url = when {
        source == SearchSource.NIVOD -> "${source.searchUrl}$encoded&page=$page"
        page <= 1 -> "${source.searchUrl}$encoded"
        else -> "${AppData.YXXQ_HOST}/search/${encoded}----------${page}---.html"
    }
    HttpUtil.get(
        url, mapOf(
            "User-Agent" to AppData.UA_CHROME_PC,
            "Referer" to "${source.host}/",
        )
    ).fold(
        onSuccess = { html -> Result.success(parseSearchResults(html, source.host, page)) },
        onFailure = { Result.failure(it) },
    )
}

private class SourceState {
    var itemList by mutableStateOf<List<SearchItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var currentPage by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)
    var hasSearched by mutableStateOf(false)
    var loadError by mutableStateOf(false)
    var loadMoreRetryKey by mutableIntStateOf(0)
    val gridState = LazyGridState()

    fun reset() {
        itemList = emptyList()
        currentPage = 1
        hasMore = true
        isLoading = false
        hasSearched = false
        loadError = false
        loadMoreRetryKey = 0
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovieSearchScreen(
    initialQuery: String = "",
    onBack: () -> Unit = {},
    onPlayVideo: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchText by remember { mutableStateOf(initialQuery) }
    var activeQuery by remember { mutableStateOf(initialQuery) }

    val sources = remember { SearchSource.entries }
    val sourceStates = remember { sources.associateWith { SourceState() } }
    var orderedSources by remember { mutableStateOf<List<SearchSource>>(sources) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { orderedSources.size })
    val tabScrollState = rememberLazyListState()
    var showSourceSheet by remember { mutableStateOf(false) }
    val sourceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    LaunchedEffect(activeQuery, retryKey) {
        if (activeQuery.isNotEmpty()) {
            sourceStates.values.forEach { it.reset() }
            orderedSources = sources
            val completed = mutableListOf<SearchSource>()
            coroutineScope {
                sources.forEach { source ->
                    launch {
                        val state = sourceStates.getValue(source)
                        state.isLoading = true
                        fetchPage(source, activeQuery, 1).fold(
                            onSuccess = { (items, pageInfo) ->
                                state.itemList = items
                                state.hasMore = pageInfo.hasNext
                            },
                            onFailure = {
                                state.itemList = emptyList()
                                state.hasMore = false
                                state.loadError = true
                            },
                        )
                        state.isLoading = false
                        state.hasSearched = true
                        completed += source
                        orderedSources = completed + sources.filterNot { it in completed }
                    }
                }
            }
        }
    }

    fun triggerSearch(query: String) {
        if (query.isNotBlank()) {
            focusManager.clearFocus()
            activeQuery = query
        }
    }

    fun retrySearch() {
        if (activeQuery.isNotBlank()) {
            retryKey++
        }
    }

    fun retryLoadMore(source: SearchSource) {
        val state = sourceStates.getValue(source)
        if (activeQuery.isNotEmpty() && state.itemList.isNotEmpty() && !state.isLoading) {
            state.loadError = false
            state.loadMoreRetryKey++
        }
    }

    fun loadMore(source: SearchSource) {
        val state = sourceStates.getValue(source)
        if (state.hasMore && !state.isLoading) {
            state.currentPage++
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(2.dp))
                        TextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = {
                                Text(
                                    "输入关键词搜索",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { triggerSearch(searchText) }),
                        )
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                onClick = { searchText = "" },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close_outlined),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { triggerSearch(searchText) }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "搜索",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PagerTabFollowEffect(pagerState = pagerState, tabScrollState = tabScrollState)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    state = tabScrollState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(orderedSources) { index, source ->
                        val selected = pagerState.currentPage == index
                        val chipColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(200),
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                        )
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(chipColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = source.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1,
                            )
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = { showSourceSheet = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_double_arrow_right_outlined),
                        contentDescription = "展开",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                ) { page ->
                    val source = orderedSources[page]
                    SourceTabContent(
                        source = source,
                        state = sourceStates.getValue(source),
                        activeQuery = activeQuery,
                        retrySearch = { retrySearch() },
                        onRetryLoadMore = { retryLoadMore(source) },
                        onLoadMore = { loadMore(source) },
                        onPlayVideo = onPlayVideo,
                    )
                }
            }
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            sheetState = sourceSheetState,
            dragHandle = { SimpleDragHandle() },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    sources.forEach { source ->
                        val pageIndex = orderedSources.indexOf(source)
                        val isSelected = pageIndex == pagerState.currentPage
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        val txtColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(bgColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pageIndex)
                                        }
                                        showSourceSheet = false
                                    },
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = source.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = txtColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceTabContent(
    source: SearchSource,
    state: SourceState,
    activeQuery: String,
    retrySearch: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val gridState = state.gridState

    LaunchedEffect(source, state.currentPage, activeQuery, state.loadMoreRetryKey) {
        if (activeQuery.isEmpty() || state.currentPage <= 1) return@LaunchedEffect
        state.isLoading = true
        fetchPage(source, activeQuery, state.currentPage).fold(
            onSuccess = { (items, pageInfo) ->
                state.itemList += items
                state.hasMore = pageInfo.hasNext
            },
            onFailure = {
                state.hasMore = false
                state.loadError = true
            },
        )
        state.isLoading = false
    }

    LaunchedEffect(source) {
        gridState.scrollToItem(0)
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            if (total == 0) return@snapshotFlow false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 5 && state.hasMore && !state.isLoading
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    var lastKnownOffset by remember(source) { mutableIntStateOf(0) }
    var isScrollingDown by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        isScrollingDown = false
        lastKnownOffset = 0
        snapshotFlow {
            gridState.isScrollInProgress to
                    (gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset)
        }.collect { (isScrolling, offsetState) ->
            if (isScrolling) {
                val (index, offset) = offsetState
                val currentTotal = index * 10000 + offset
                isScrollingDown = currentTotal > lastKnownOffset
                lastKnownOffset = currentTotal
            }
        }
    }

    val isAtTop by remember(source) {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset < 50
        }
    }

    val showFab by remember(source) {
        derivedStateOf {
            !isAtTop && !isScrollingDown
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.isLoading && state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChrysanthemumLoading()
            }
        } else if (state.loadError && state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { retrySearch() }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "点击重试",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else if (state.itemList.isEmpty() && state.hasSearched) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 12.dp,
                    bottom = 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.itemList, key = { it.link }) { item ->
                    SearchGridItem(
                        item = item,
                        onClick = {
                            focusManager.clearFocus()
                            onPlayVideo(item.link)
                        },
                    )
                }
                if (state.isLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChrysanthemumLoading(size = 36.dp)
                        }
                    }
                } else if (state.loadError) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { onRetryLoadMore() }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = "点击重试",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showFab) {
            FloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
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

@Composable
private fun SearchGridItem(item: SearchItem, onClick: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                val intent = Intent(Intent.ACTION_VIEW, item.cover.toUri())
                context.startActivity(intent)
            },
        ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.cover)
                        .crossfade(true)
                        .httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Referer", item.host)
                                .set("User-Agent", AppData.UA_CHROME_M)
                                .build()
                        ).build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )

                if (item.note.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.medium,
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = item.note,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (item.subtitle.isNotEmpty()) {
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
                        Text(
                            text = item.subtitle,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (item.year.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.year,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}