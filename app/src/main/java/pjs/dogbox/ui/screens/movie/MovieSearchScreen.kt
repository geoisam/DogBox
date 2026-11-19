package pjs.dogbox.ui.screens.movie

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.data.repository.ApiSite
import pjs.dogbox.data.repository.ApiSitesRepository
import pjs.dogbox.data.repository.AppRepository
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.ui.components.CoverImage
import pjs.dogbox.ui.components.PagerTabFollowEffect
import pjs.dogbox.ui.components.ScrollToTopFab
import pjs.dogbox.utils.network.HttpUtil
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Tune
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

internal data class SearchSource(
    val key: String,
    val label: String,
    val host: String,
    /** CMS JSON 接口片源（assets/json/sites.json）的接口基址 api */
    val api: String,
) {
    companion object {
        fun fromApiSite(site: ApiSite): SearchSource = SearchSource(
            key = site.key,
            label = site.name,
            host = ApiSitesRepository.originOf(site.api),
            api = site.api,
        )
    }
}

internal data class SearchItem(
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

private val vodListJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 单页抓取最大尝试次数（含首次请求），超时/异常/首页空结果均计入 */
private const val MAX_RETRY_ATTEMPTS = 3

/** 重试退避基数：第 1 次重试等 500ms，第 2 次等 1000ms（指数退避） */
private const val RETRY_BACKOFF_BASE_MS = 500L

/** 首轮搜索结束后，对失败源统一延迟补搜，给站点恢复时间 */
private const val AUTO_RETRY_DELAY_MS = 1500L

private fun JsonObject.optStr(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull?.trim() ?: ""

/**
 * CMS JSON 接口搜索解析（sites.json 片源）。
 * vod_id→详情ID，vod_name→标题，vod_class→封面底部阴影，
 * vod_year→年份，vod_remarks→封面右上角，vod_pic→封面。
 */
private fun parseApiSearchResults(
    json: String,
    source: SearchSource,
    currentPage: Int = 1,
): Pair<List<SearchItem>, PageInfo> {
    val api = source.api
    val origin = ApiSitesRepository.originOf(api)
    val root = vodListJson.parseToJsonElement(json).jsonObject
    val arr = root["list"] as? JsonArray ?: JsonArray(emptyList())
    val items = arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val vodId = o.optStr("vod_id")
        if (vodId.isBlank()) return@mapNotNull null
        val title = o.optStr("vod_name")
        if (title.isBlank()) return@mapNotNull null
        val coverRaw = o.optStr("vod_pic")
        val cover = when {
            coverRaw.startsWith("http") -> coverRaw
            coverRaw.startsWith("//") -> "https:$coverRaw"
            coverRaw.startsWith("/") -> origin + coverRaw
            coverRaw.isNotBlank() -> "$origin/$coverRaw"
            else -> ""
        }
        SearchItem(
            link = ApiSitesRepository.detailUrl(api, vodId),
            cover = cover,
            title = title,
            note = o.optStr("vod_remarks"),
            year = o.optStr("vod_year"),
            subtitle = o.optStr("vod_class"),
            host = origin,
        )
    }
    val pageNo = root["page"]?.jsonPrimitive?.intOrNull ?: currentPage
    val pageCount = root["pagecount"]?.jsonPrimitive?.intOrNull ?: 0
    val hasNext = if (pageCount > 0) pageNo < pageCount else items.isNotEmpty()
    return items to PageInfo(hasNext = hasNext, nextUrl = null)
}

private suspend fun fetchPage(
    source: SearchSource,
    keyword: String,
    page: Int,
): Result<Pair<List<SearchItem>, PageInfo>> = withContext(Dispatchers.IO) {
    val url = ApiSitesRepository.searchUrl(source.api, keyword, page)

    var lastError: Throwable? = null
    var lastEmpty = false
    // 无限翻页到末尾的空页属于正常情况；仅首页的空结果视为瞬时故障需要重试
    val retryOnEmpty = page == 1
    var attempt = 0
    while (attempt < MAX_RETRY_ATTEMPTS) {
        try {
            val html = HttpUtil.get(
                url, mapOf(
                    "User-Agent" to AppConfig.UA_CHROME_PC,
                    "Referer" to "${source.host}/",
                ),
                // 搜索请求用短超时：失败源更快进入重试循环，而非等满 15s
                // 连接 5s + 读取 8s，总上限 10s
                requestTimeoutMillis = 10_000,
                connectTimeoutMillis = 5_000,
                socketTimeoutMillis = 8_000,
            ).getOrThrow()
            val result = parseApiSearchResults(html, source, page)
            // 部分站点 CDN 抖动会返回 HTTP 200 但内容为空/残缺 HTML，解析成空列表，
            // 这是最常见的“需要重试”场景，首页空结果也纳入重试
            if (result.first.isEmpty() && retryOnEmpty && attempt < MAX_RETRY_ATTEMPTS - 1) {
                lastEmpty = true
            } else {
                return@withContext Result.success(result)
            }
        } catch (e: Throwable) {
            lastError = e
            lastEmpty = false
        }
        attempt++
        if (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                // 指数退避，避免给过载的站点雪上加霜
                delay(RETRY_BACKOFF_BASE_MS * attempt)
            } catch (_: Throwable) {
            }
        }
    }
    Result.failure(
        lastError ?: if (lastEmpty) IOException("搜索结果为空（已重试）")
        else IOException("未知网络错误")
    )
}

internal class SourceState {
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

internal class MovieSearchScreenState(
    initialQuery: String,
    apiSites: List<ApiSite> = emptyList(),
) {
    val searchText = mutableStateOf(initialQuery)
    val activeQuery = mutableStateOf(initialQuery)
    val sources: List<SearchSource> = apiSites.map { SearchSource.fromApiSite(it) }
    val sourceStates = sources.associateWith { SourceState() }
    val orderedSources = mutableStateOf<List<SearchSource>>(sources)
    val retryKey = mutableIntStateOf(0)
    val lastSearchKey = mutableStateOf("")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MovieSearchScreen(
    state: MovieSearchScreenState,
    onBack: () -> Unit = {},
    onPlayVideo: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var searchText by state.searchText
    var activeQuery by state.activeQuery

    val context = LocalContext.current

    val sources = state.sources
    val sourceStates = state.sourceStates
    var orderedSources by state.orderedSources
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { orderedSources.size })
    val tabScrollState = rememberLazyListState()
    var showSourceSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var searchExpanded by remember { mutableStateOf(false) }

    var retryKey by state.retryKey
    var lastSearchKey by state.lastSearchKey

    suspend fun searchOne(source: SearchSource, query: String) {
        val s = sourceStates.getValue(source)
        if (s.isLoading) return
        s.isLoading = true
        s.loadError = false
        s.currentPage = 1
        s.hasMore = true
        try {
            fetchPage(source, query, 1).fold(
                onSuccess = { (items, pageInfo) ->
                    s.itemList = items.distinctBy { it.link }
                    s.hasMore = pageInfo.hasNext
                    s.loadError = false
                },
                onFailure = {
                    s.itemList = emptyList()
                    s.hasMore = false
                    s.loadError = true
                },
            )
        } catch (e: Throwable) {
            s.itemList = emptyList()
            s.hasMore = false
            s.loadError = true
        } finally {
            s.isLoading = false
            s.hasSearched = true
        }
    }

    LaunchedEffect(activeQuery, retryKey) {
        val searchKey = "$activeQuery|$retryKey"
        if (lastSearchKey == searchKey) return@LaunchedEffect
        lastSearchKey = searchKey
        if (activeQuery.isNotEmpty()) {
            sourceStates.values.forEach { it.reset() }
            orderedSources = sources
            // 按设置里的“搜索线程”限制并发请求数，避免所有片源同时请求导致卡死/网络拥塞
            val semaphore = Semaphore(AppRepository.getSearchThreads(context))
            val completed = mutableListOf<SearchSource>()
            coroutineScope {
                sources.forEach { source ->
                    launch {
                        semaphore.withPermit { searchOne(source, activeQuery) }
                        completed += source
                        orderedSources = completed + sources.filterNot { it in completed }
                    }
                }
            }
            // 第二轮：对第一轮失败（网络抖动/接口超时/空结果）的源自动补搜一次，
            // 新搜索会取消本协程，因此不会与后续搜索互相干扰
            val failedSources = sources.filter {
                sourceStates.getValue(it).run { loadError && itemList.isEmpty() }
            }
            if (failedSources.isNotEmpty()) {
                delay(AUTO_RETRY_DELAY_MS)
                coroutineScope {
                    failedSources.forEach { source ->
                        launch { semaphore.withPermit { searchOne(source, activeQuery) } }
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

    fun retrySource(source: SearchSource) {
        if (activeQuery.isBlank()) return
        scope.launch {
            // 仅重置当前源的分页，不影响其它源
            searchOne(source, activeQuery)
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

    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIconButton(onClick = onBack) {
                    MiuixIcon(
                        imageVector = MiuixIcons.Glass.ChevronBackward,
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp),
                    )
                }
                InputField(
                    query = searchText,
                    onQueryChange = { searchText = it },
                    onSearch = { triggerSearch(it) },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    label = "输入关键词搜索",
                )
                MiuixIconButton(onClick = { /* TODO: 排序 / 筛选 */ }) {
                    MiuixIcon(
                        imageVector = MiuixIcons.Glass.Tune,
                        contentDescription = "筛选",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
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
                            targetValue = if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(200),
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.onSurfaceSecondary,
                            animationSpec = tween(200),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
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
                            MiuixText(
                                text = source.label,
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1,
                            )
                        }
                    }
                }

                MiuixIconButton(
                    onClick = { showSourceSheet = true },
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(32.dp),
                ) {
                    MiuixIcon(
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
                        onRetrySearch = { retrySource(source) },
                        onRetryLoadMore = { retryLoadMore(source) },
                        onLoadMore = { loadMore(source) },
                        onPlayVideo = onPlayVideo,
                        onViewImage = onViewImage,
                    )
                }
            }
        }
    }

    AppBottomSheet(
        show = showSourceSheet,
        onDismissRequest = { showSourceSheet = false },
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            orderedSources.forEach { source ->
                val pageIndex = orderedSources.indexOf(source)
                val isSelected = pageIndex == pagerState.currentPage
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.surfaceContainerHigh,
                )
                val txtColor by animateColorAsState(
                    targetValue = if (isSelected) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
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
                    MiuixText(
                        text = source.label,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = txtColor,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SourceTabContent(
    source: SearchSource,
    state: SourceState,
    activeQuery: String,
    onRetrySearch: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val gridState = state.gridState

    LaunchedEffect(source, state.currentPage, activeQuery, state.loadMoreRetryKey) {
        if (activeQuery.isEmpty() || state.currentPage <= 1) return@LaunchedEffect
        state.isLoading = true
        try {
            fetchPage(source, activeQuery, state.currentPage).fold(
                onSuccess = { (items, pageInfo) ->
                    // 无限页：空页即止；有去重后无新增也视为止，避免重复数据死循环
                    if (items.isEmpty()) {
                        state.hasMore = false
                    } else {
                        val newList = (state.itemList + items).distinctBy { it.link }
                        val hasNew = newList.size > state.itemList.size
                        state.itemList = newList
                        // pageInfo.hasNext 已按无限页置为 items.isNotEmpty()，再结合去重结果
                        state.hasMore = pageInfo.hasNext && hasNew
                        // 若无新增但仍有数据，仍更新为空判断已在上层处理，此分支不重复赋值
                        if (!hasNew) {
                            // 已无新增，去重后未增长，视为到达末页
                        }
                    }
                },
                onFailure = {
                    state.hasMore = false
                    state.loadError = true
                },
            )
        } catch (e: Throwable) {
            state.hasMore = false
            state.loadError = true
        } finally {
            state.isLoading = false
        }
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
                CircularProgressIndicator(size = 56.dp)
            }
        } else if (state.loadError && state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.primaryContainer)
                        .clickable { onRetrySearch() }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    MiuixText(
                        text = "点击重试",
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else if (state.itemList.isEmpty() && state.hasSearched) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MiuixText("未找到相关结果", color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        } else if (state.itemList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MiuixText("输入关键词开始搜索", color = MiuixTheme.colorScheme.onSurfaceSecondary)
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
                        onViewImage = { _, _, _ -> onViewImage(item.cover, item.host, item.title) },
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
                            CircularProgressIndicator(size = 36.dp)
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
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MiuixTheme.colorScheme.primaryContainer)
                                    .clickable { onRetryLoadMore() }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                            ) {
                                MiuixText(
                                    text = "点击重试",
                                    color = MiuixTheme.colorScheme.onPrimaryContainer,
                                    style = MiuixTheme.textStyles.footnote1,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showFab) {
            ScrollToTopFab(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            )
        }
    }
}

@Composable
private fun SearchGridItem(
    item: SearchItem,
    onClick: () -> Unit,
    onViewImage: (String, String, String) -> Unit
) {
    Card(
        onClick = onClick,
        onLongPress = { onViewImage(item.cover, item.host, item.title) },
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.cover)
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
                        .clip(RoundedCornerShape(8.dp)),
                )

                if (item.note.isNotEmpty()) {
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
                            text = item.note,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            style = MiuixTheme.textStyles.footnote1,
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
                        MiuixText(
                            text = item.subtitle,
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote1,
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
                MiuixText(
                    text = item.title,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (item.year.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    MiuixText(
                        text = item.year,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}