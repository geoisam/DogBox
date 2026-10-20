package pjs.dogbox.ui.screens.discover

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.ui.components.CoverImage
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixLoading
import pjs.dogbox.ui.components.MiuixSubScreen
import pjs.dogbox.ui.components.PagerTabFollowEffect
import pjs.dogbox.ui.navigation.TabStateCache
import pjs.dogbox.utils.network.HttpUtil
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFab
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.Text as MiuixText

internal data class AnimeItem(
    val cover: String,
    val title: String,
    val score: Double,
    val isFinish: Int,
    val indexShow: String,
    val order: String,
    val seasonId: Int,
)

internal data class FilterData(
    val sortOptions: List<Pair<Int, String>>,
    val orderOptions: List<Pair<Int, String>>,
    val statusOptions: List<Pair<Int, String>>,
    val yearOptions: List<Pair<String, String>>,
    val styleOptions: List<Pair<Int, String>>,
)

internal class AnimeRankOrderState {
    var currentPage by mutableIntStateOf(1)
    var animeList by mutableStateOf(emptyList<AnimeItem>())
    var isLoading by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var isRefreshing by mutableStateOf(false)
    var refreshKey by mutableIntStateOf(0)
    var prevRefreshKey by mutableIntStateOf(0)
    var lastFetchKey by mutableStateOf("")
    val gridState = LazyGridState()
}

internal class AnimeRankScreenState {
    val filterState = mutableIntStateOf(STATE_LOADING)
    val filterData = mutableStateOf<FilterData?>(null)
    val isFinish = mutableIntStateOf(-1)
    val selectedYear = mutableStateOf("-1")
    val selectedStyleId = mutableIntStateOf(-1)
    val sortMode = mutableIntStateOf(0)
    val showFilters = mutableStateOf(true)
    val tabCache = TabStateCache { AnimeRankOrderState() }
}

private const val STATE_LOADING = 0
private const val STATE_LOADED = 1
private const val STATE_ERROR = 2

private fun parseBilibiliJson(text: String): FilterData {
    val json = Json.parseToJsonElement(text).jsonObject
    fun parsePairs(arr: JsonArray) = arr.map { e ->
        val o = e.jsonObject
        (o["v"]?.jsonPrimitive?.int ?: 0) to (o["n"]?.jsonPrimitive?.contentOrNull ?: "")
    }

    fun parseYearPairs(arr: JsonArray) = arr.map { e ->
        val o = e.jsonObject
        val p = o["v"]?.jsonPrimitive
        (p?.contentOrNull ?: p?.int?.toString() ?: "-1") to (o["n"]?.jsonPrimitive?.contentOrNull
            ?: "")
    }
    return FilterData(
        sortOptions = parsePairs(json["sort"]?.jsonArray ?: JsonArray(emptyList())),
        orderOptions = parsePairs(json["order"]?.jsonArray ?: JsonArray(emptyList())),
        statusOptions = parsePairs(json["status"]?.jsonArray ?: JsonArray(emptyList())),
        yearOptions = parseYearPairs(json["year"]?.jsonArray ?: JsonArray(emptyList())),
        styleOptions = parsePairs(json["style"]?.jsonArray ?: JsonArray(emptyList())),
    )
}

private fun buildUrl(
    order: Int, sort: Int, page: Int,
    isFinish: Int, year: String, styleId: Int,
): String {
    return "https://api.bilibili.com/pgc/season/index/result?" +
            "season_version=-1&spoken_language_type=-1&area=-1" +
            "&is_finish=$isFinish&copyright=-1&season_status=-1&season_month=-1" +
            "&year=$year&style_id=$styleId&order=$order&st=1&sort=$sort" +
            "&page=$page&season_type=1&pagesize=20&type=1"
}

@Composable
internal fun AnimeRankScreen(
    state: AnimeRankScreenState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    var filterState by state.filterState
    var filterData by state.filterData
    var isFinish by state.isFinish
    var selectedYear by state.selectedYear
    var selectedStyleId by state.selectedStyleId
    var sortMode by state.sortMode

    LaunchedEffect(Unit) {
        if (filterState != STATE_LOADING) return@LaunchedEffect
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                val text = context.assets.open("json/bilibili.json")
                    .bufferedReader().use { it.readText() }
                filterData = parseBilibiliJson(text)
                filterState = STATE_LOADED
                break
            } catch (_: Exception) {
                if (attempts >= 2) filterState = STATE_ERROR
            }
        }
    }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        pageCount = { filterData?.orderOptions?.size ?: 1 }
    )
    val tabCache = state.tabCache
    var showFilters by state.showFilters

    MiuixSubScreen(
        title = "哔哩番剧",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (filterState) {
                STATE_LOADING -> {
                    MiuixLoading(modifier = Modifier.fillMaxSize())
                }

                STATE_ERROR -> {
                    MiuixEmptyText(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    filterData?.let { data ->
                        fun resetAllTabs() {
                            tabCache.forEach { s ->
                                s.currentPage = 1
                                s.animeList = emptyList()
                                s.hasMore = true
                                s.lastFetchKey = ""
                            }
                        }
                        OrderTabBar(
                            options = data.orderOptions,
                            pagerState = pagerState,
                            onSelect = { value ->
                                val idx = data.orderOptions.indexOfFirst { it.first == value }
                                if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                            },
                        )

                        // 筛选栏外提常驻：切换标签时仅标签高亮变化，筛选栏不跟随刷新；
                        // 卡片区各自转圈加载。
                        AnimatedVisibility(
                            visible = showFilters,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            FilterBar(
                                filterData = data,
                                sortMode = sortMode,
                                isFinish = isFinish,
                                selectedYear = selectedYear,
                                selectedStyleId = selectedStyleId,
                                onSortChange = {
                                    if (it != sortMode) {
                                        sortMode = it
                                        resetAllTabs()
                                    }
                                },
                                onIsFinishChange = {
                                    if (it != isFinish) {
                                        isFinish = it
                                        resetAllTabs()
                                    }
                                },
                                onYearChange = {
                                    if (it != selectedYear) {
                                        selectedYear = it
                                        resetAllTabs()
                                    }
                                },
                                onStyleIdChange = {
                                    if (it != selectedStyleId) {
                                        selectedStyleId = it
                                        resetAllTabs()
                                    }
                                },
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFilters = !showFilters }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixIcon(
                                painter = painterResource(if (showFilters) R.drawable.keyboard_double_arrow_up_outlined else R.drawable.keyboard_double_arrow_down_outlined),
                                contentDescription = if (showFilters) "收起" else "展开",
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                            MiuixText(
                                text = if (showFilters) "收起筛选" else "展开筛选",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            beyondViewportPageCount = 0,
                        ) { page ->
                            key(data.orderOptions.getOrNull(page)?.first ?: page) {
                                AnimeRankOrderContent(
                                    order = data.orderOptions.getOrNull(page)?.first ?: 1,
                                    isFinish = isFinish,
                                    selectedYear = selectedYear,
                                    selectedStyleId = selectedStyleId,
                                    sortMode = sortMode,
                                    onSearchMovie = onSearchMovie,
                                    onViewImage = onViewImage,
                                    tabState = tabCache.get(
                                        (data.orderOptions.getOrNull(page)?.first ?: 1).toString()
                                    ),
                                    showFilters = showFilters,
                                    onShowFiltersChange = { showFilters = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeRankOrderContent(
    order: Int,
    isFinish: Int,
    selectedYear: String,
    selectedStyleId: Int,
    sortMode: Int,
    onSearchMovie: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    tabState: AnimeRankOrderState,
    showFilters: Boolean,
    onShowFiltersChange: (Boolean) -> Unit,
) {
    LaunchedEffect(
        order,
        tabState.currentPage,
        isFinish,
        selectedYear,
        selectedStyleId,
        sortMode,
        tabState.refreshKey
    ) {
        val fetchKey =
            "$order|${tabState.currentPage}|$isFinish|$selectedYear|$selectedStyleId|$sortMode|${tabState.refreshKey}"
        if (tabState.lastFetchKey == fetchKey && tabState.animeList.isNotEmpty()) {
            return@LaunchedEffect
        }
        tabState.lastFetchKey = fetchKey
        val isRefresh = tabState.prevRefreshKey != tabState.refreshKey
        tabState.prevRefreshKey = tabState.refreshKey
        if (tabState.currentPage == 1 && !isRefresh) {
            // 筛选切换/切标签：清空旧卡片，走默认全屏转圈；下拉刷新保留旧列表
            tabState.animeList = emptyList()
        }
        tabState.isLoading = true
        tabState.isRefreshing = isRefresh
        val url = buildUrl(
            order = order,
            sort = sortMode,
            page = tabState.currentPage,
            isFinish = isFinish,
            year = selectedYear,
            styleId = selectedStyleId,
        )
        withContext(Dispatchers.IO) {
            HttpUtil.get(url, mapOf("User-Agent" to AppConfig.UA_CHROME_PC))
        }.onSuccess { response ->
            try {
                val json = Json.parseToJsonElement(response).jsonObject
                val resultObj = json["result"]?.jsonObject ?: json["data"]?.jsonObject
                val list = resultObj?.get("list")?.jsonArray
                if (list != null) {
                    val items = list.mapNotNull { element ->
                        val obj = element.jsonObject
                        val rawCover =
                            obj["cover"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val cover = if (rawCover.startsWith("http://")) rawCover.replaceFirst(
                            "http://",
                            "https://"
                        ) else rawCover

                        AnimeItem(
                            cover = cover,
                            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                            score = obj["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            isFinish = obj["is_finish"]?.jsonPrimitive?.int ?: 0,
                            indexShow = obj["index_show"]?.jsonPrimitive?.contentOrNull ?: "",
                            order = obj["order"]?.jsonPrimitive?.contentOrNull ?: "",
                            seasonId = obj["season_id"]?.jsonPrimitive?.int ?: 0,
                        )
                    }
                    if (tabState.currentPage == 1) tabState.animeList =
                        items else tabState.animeList += items
                    tabState.hasMore = items.size >= 20 && list.size >= 20
                } else {
                    tabState.hasMore = false
                }
            } catch (_: Exception) {
                tabState.hasMore = false
            }
        }.onFailure {
            tabState.hasMore = false
        }
        tabState.isLoading = false
        tabState.isRefreshing = false
        if (tabState.currentPage == 1) {
            try {
                tabState.gridState.scrollToItem(0)
            } catch (_: Exception) {
            }
        }
    }

    val lazyGridState = tabState.gridState

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = lazyGridState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisible = lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 5 && tabState.hasMore && !tabState.isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && tabState.hasMore && !tabState.isLoading) {
            tabState.currentPage++
        }
    }

    val scope = rememberCoroutineScope()

    val isAtTop by remember {
        derivedStateOf {
            lazyGridState.firstVisibleItemIndex == 0 && lazyGridState.firstVisibleItemScrollOffset < 50
        }
    }

    var lastKnownOffset by remember { mutableIntStateOf(0) }
    var isScrollingDown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow {
            lazyGridState.isScrollInProgress to
                    (lazyGridState.firstVisibleItemIndex to lazyGridState.firstVisibleItemScrollOffset)
        }.collect { (isScrolling, state) ->
            if (isScrolling) {
                val (index, offset) = state
                val currentTotal = index * 10000 + offset
                isScrollingDown = currentTotal > lastKnownOffset
                lastKnownOffset = currentTotal
                if (isScrollingDown) {
                    onShowFiltersChange(false)
                }
            }
        }
    }

    val overscrollConnection = remember(isAtTop, showFilters) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && isAtTop && !showFilters) {
                    onShowFiltersChange(true)
                }
                return Offset.Zero
            }
        }
    }

    val showFab by remember {
        derivedStateOf {
            !isAtTop && !isScrollingDown
        }
    }

    // 筛选栏已外提常驻，这里只负责卡片区：空列表+加载中 => 默认全屏转圈
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(overscrollConnection),
    ) {
        MiuixPullToRefresh(
            isRefreshing = tabState.isRefreshing,
            onRefresh = {
                tabState.currentPage = 1
                tabState.refreshKey++
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                tabState.animeList.isEmpty() && tabState.isLoading -> {
                    MiuixLoading(modifier = Modifier.fillMaxSize())
                }

                tabState.animeList.isEmpty() -> {
                    MiuixEmptyText(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = lazyGridState,
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(tabState.animeList, key = { it.seasonId }) { item ->
                            AnimeGridItem(
                                item = item,
                                onClick = { onSearchMovie(item.title) },
                                onViewImage = { _, _, _ ->
                                    onViewImage(
                                        item.cover.substringBefore("@"),
                                        "https://www.bilibili.com/anime/",
                                        item.title
                                    )
                                },
                            )
                        }

                        if (tabState.isLoading) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MiuixCircularProgressIndicator(size = 36.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFab) {
            MiuixFab(
                onClick = {
                    scope.launch {
                        lazyGridState.animateScrollToItem(0)
                        onShowFiltersChange(true)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                MiuixIcon(
                    painter = painterResource(R.drawable.arrow_upward_outlined),
                    contentDescription = "回到顶部",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun AnimeGridItem(
    item: AnimeItem,
    onClick: () -> Unit,
    onViewImage: (String, String, String) -> Unit,
) {
    MiuixCard(
        onClick = onClick,
        onLongPress = {
            onViewImage(
                item.cover.substringBefore("@"),
                "https://www.bilibili.com/anime/",
                item.title
            )
        },
        colors = MiuixCardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                val thumbnailUrl =
                    if (item.cover.contains("@")) {
                        item.cover
                    } else {
                        "${item.cover}@400w_600h.webp"
                    }
                CoverImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Referer", "https://www.bilibili.com/anime/")
                                .build()
                        ).build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp)),
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
                        text = if (item.score > 0.0) String.format("%.1f", item.score)
                        else if (item.isFinish == 1) "已完结" else if (item.isFinish == 0) "连载中" else "0.0",
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (item.order.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                )
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = item.order,
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
                if (item.indexShow.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    MiuixText(
                        text = item.indexShow,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterRowWithDialog(
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    title: String = "选择",
) {
    var showDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selectedValue
                val chipColor by animateColorAsState(
                    targetValue = if (isSelected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = tween(200),
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurfaceSecondary,
                    animationSpec = tween(200),
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(value) },
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    MiuixText(
                        text = label,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }

        MiuixIconButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .padding(start = 4.dp)
                .size(32.dp),
        ) {
            MiuixIcon(
                painter = painterResource(R.drawable.keyboard_double_arrow_right_outlined),
                contentDescription = "展开",
                modifier = Modifier.size(18.dp),
            )
        }
    }

    AppBottomSheet(
        show = showDialog,
        onDismissRequest = { showDialog = false },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selectedValue
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = tween(200),
                    )
                    val txtColor by animateColorAsState(
                        targetValue = if (isSelected) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurface,
                        animationSpec = tween(200),
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onSelect(value)
                                    showDialog = false
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        MiuixText(
                            text = label,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = txtColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderTabBar(
    options: List<Pair<Int, String>>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }

    val tabScrollState = rememberLazyListState()
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
            itemsIndexed(options) { index, (value, label) ->
                val selected = index == pagerState.currentPage
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
                        .clip(RoundedCornerShape(50))
                        .background(chipColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(value) },
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    MiuixText(
                        text = label,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }

        MiuixIconButton(
            onClick = { showSheet = true },
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

    AppBottomSheet(
        show = showSheet,
        onDismissRequest = { showSheet = false },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == options.getOrNull(pagerState.currentPage)?.first
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
                                    onSelect(value)
                                    showSheet = false
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        MiuixText(
                            text = label,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = txtColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FilterBar(
    filterData: FilterData,
    sortMode: Int,
    isFinish: Int,
    selectedYear: String,
    selectedStyleId: Int,
    onSortChange: (Int) -> Unit,
    onIsFinishChange: (Int) -> Unit,
    onYearChange: (String) -> Unit,
    onStyleIdChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterRowWithDialog(
            options = filterData.sortOptions,
            selectedValue = sortMode,
            onSelect = onSortChange,
            title = "排序方式",
        )
        FilterRowWithDialog(
            options = filterData.statusOptions,
            selectedValue = isFinish,
            onSelect = onIsFinishChange,
            title = "连载状态",
        )
        FilterRowWithDialog(
            options = filterData.yearOptions,
            selectedValue = selectedYear,
            onSelect = onYearChange,
            title = "年份",
        )
        FilterRowWithDialog(
            options = filterData.styleOptions,
            selectedValue = selectedStyleId,
            onSelect = onStyleIdChange,
            title = "风格",
        )
    }
}
