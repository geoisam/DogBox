package pjs.dogbox.ui.screens

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
import androidx.compose.ui.unit.Dp
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.data.model.AppConfig
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.ui.components.CoverImage
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixLoading
import pjs.dogbox.ui.components.PagerTabFollowEffect
import pjs.dogbox.ui.navigation.TabStateCache
import pjs.dogbox.utils.network.HttpUtil
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URLEncoder
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

internal data class HomeItem(
    val id: String,
    val cover: String,
    val coverLarge: String,
    val title: String,
    val subtitle: String,
    val rating: Double,
    val year: String,
)

private const val DOUBAN_API = "https://frodo.douban.com/api/v2"
private const val DOUBAN_APIKEY = "0ac44ae016490db2204ce0a042db2916"

private data class TabDef(
    val label: String,
    val apiType: String,
    val fixedTag: String?,
    val categories: List<String>,
    val useSuggestionApi: Boolean = false,
)

internal class FilterableTabState {
    var itemList by mutableStateOf(emptyList<HomeItem>())
    var isLoading by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var loadPage by mutableIntStateOf(0)
    var isRefreshing by mutableStateOf(false)
    var refreshKey by mutableIntStateOf(0)
    var prevRefreshKey by mutableIntStateOf(0)
    var lastReloadKey by mutableStateOf("")
    var lastLoadPage by mutableIntStateOf(-1)
    var firstApiLoaded by mutableStateOf(false)

    var selectedSort by mutableStateOf("U")
    var selectedYear by mutableStateOf("")
    var selectedMovieType by mutableStateOf("")
    var selectedMovieArea by mutableStateOf("")
    var selectedTvType by mutableStateOf("")
    var selectedTvArea by mutableStateOf("")
    var selectedTvForm by mutableStateOf("")
    var selectedShowType by mutableStateOf("")
    var showFilters by mutableStateOf(true)

    val gridState = LazyGridState()
}

internal class HomeScreenState {
    val filterState = mutableIntStateOf(STATE_LOADING)
    val filterData = mutableStateOf<HomeFilterData?>(null)
    val tabCache = TabStateCache { FilterableTabState() }
    val firstTabLoaded = mutableStateOf(false)
}

private val tabs = listOf(
    TabDef("推荐", "movie", null, emptyList(), useSuggestionApi = true),
    TabDef("电影", "movie", null, listOf("sort", "year", "movie-type", "movie-area")),
    TabDef("电视剧", "tv", null, listOf("sort", "year", "tv-type", "tv-area", "tv-form")),
    TabDef("动画电影", "movie", "动画", listOf("sort", "year", "movie-type", "movie-area")),
    TabDef("动画动漫", "tv", "动画", listOf("sort", "year", "tv-type", "tv-area", "tv-form")),
    TabDef("纪录片", "movie", "纪录片", listOf("sort", "year", "movie-type", "movie-area")),
    TabDef("综艺", "tv", "综艺", listOf("sort", "year", "show-type", "tv-area", "tv-form")),
    TabDef("短片", "movie", "短片", listOf("sort", "year", "movie-type", "movie-area")),
)

@SuppressLint("DefaultLocale")
@Composable
internal fun HomeScreen(
    state: HomeScreenState,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    contentBottomPadding: Dp = 0.dp,
) {
    var filterState by state.filterState
    var filterData by state.filterData

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    LaunchedEffect(Unit) {
        if (filterState != STATE_LOADING) return@LaunchedEffect
        var attempts = 0
        while (attempts < 2) {
            attempts++
            val text = withContext(Dispatchers.IO) {
                try {
                    context.assets.open("json/douban.json")
                        .bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    null
                }
            }
            if (text != null) {
                filterData = parseHomeFilters(text)
                filterState = STATE_LOADED
                break
            } else if (attempts >= 2) {
                filterState = STATE_ERROR
            }
        }
    }

    val tabCache = state.tabCache

    var showTabSheet by remember { mutableStateOf(false) }
    var firstTabLoaded by state.firstTabLoaded

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
    ) {
        if (!firstTabLoaded) {
            MiuixLoading(modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.fillMaxSize()) {
            if (firstTabLoaded) {
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
                        itemsIndexed(tabs) { index, tab ->
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
                                                pagerState.animateScrollToPage(
                                                    index
                                                )
                                            }
                                        },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                MiuixText(
                                    text = tab.label,
                                    style = MiuixTheme.textStyles.footnote1,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    MiuixIconButton(
                        onClick = { showTabSheet = true },
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
            }

            // 筛选头外提常驻：不随 pager 页面滑动而滑动/重建，切标签时仅标签高亮变化，
            // 筛选栏本身不跟随刷新；下方卡片区各自转圈加载。
            if (firstTabLoaded && filterState == STATE_LOADED) {
                val currentTab = tabs.getOrNull(pagerState.currentPage) ?: tabs[0]
                val currentTabState = tabCache.get(currentTab.label)
                key(currentTab.label) {
                    HomeFilterHeader(
                        tab = currentTab,
                        filterData = filterData,
                        tabState = currentTabState,
                    )
                }
            }

            when (filterState) {
                STATE_LOADING -> {
                    MiuixLoading(modifier = Modifier.fillMaxSize())
                }

                STATE_ERROR -> {
                    MiuixEmptyText(modifier = Modifier.fillMaxSize())
                }

                STATE_LOADED -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        beyondViewportPageCount = 0,
                    ) { page ->
                        FilterableTabContent(
                            tab = tabs[page],
                            filterData = filterData ?: HomeFilterData(
                                sortOptions = emptyList(),
                                yearOptions = emptyList(),
                                movieTypeOptions = emptyList(),
                                movieAreaOptions = emptyList(),
                                tvTypeOptions = emptyList(),
                                tvAreaOptions = emptyList(),
                                tvFormOptions = emptyList(),
                                showTypeOptions = emptyList(),
                            ),
                            tabState = tabCache.get(tabs[page].label),
                            onItemClick = { title ->
                                onSearchMovie(title)
                            },
                            onViewImage = onViewImage,
                            onDataLoaded = { firstTabLoaded = true },
                            contentBottomPadding = contentBottomPadding,
                        )
                    }
                }
            }
        }

        AppBottomSheet(
            show = showTabSheet,
            onDismissRequest = { showTabSheet = false },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = pagerState.currentPage == index
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
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                        showTabSheet = false
                                    },
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            MiuixText(
                                text = tab.label,
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
    }
}

@Composable
private fun HomeFilterHeader(
    tab: TabDef,
    filterData: HomeFilterData?,
    tabState: FilterableTabState,
) {
    if (tab.categories.isEmpty()) return
    // tabState 对象本身稳定（来自 TabStateCache），回调只创建一次；
    // 切换某个筛选时，其它行的 onSelect 实例不变，可跳过重组
    val selectHandlers = remember(tabState) {
        mapOf<String, (String) -> Unit>(
            "sort" to { v -> tabState.selectedSort = v },
            "year" to { v -> tabState.selectedYear = v },
            "movie-type" to { v -> tabState.selectedMovieType = v },
            "movie-area" to { v -> tabState.selectedMovieArea = v },
            "tv-type" to { v -> tabState.selectedTvType = v },
            "tv-area" to { v -> tabState.selectedTvArea = v },
            "tv-form" to { v -> tabState.selectedTvForm = v },
            "show-type" to { v -> tabState.selectedShowType = v },
        )
    }

    fun optionsFor(category: String): List<Pair<String, String>> {
        val d = filterData ?: return emptyList()
        return when (category) {
            "sort" -> d.sortOptions
            "year" -> d.yearOptions
            "movie-type" -> d.movieTypeOptions
            "movie-area" -> d.movieAreaOptions
            "tv-type" -> d.tvTypeOptions
            "tv-area" -> d.tvAreaOptions
            "tv-form" -> d.tvFormOptions
            "show-type" -> d.showTypeOptions
            else -> emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = tabState.showFilters,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                tab.categories.forEach { category ->
                    key(tab.label + "_" + category) {
                        val options = optionsFor(category)
                        val onSelect = selectHandlers[category]
                        if (options.isNotEmpty() && onSelect != null) {
                            val selectedVal = when (category) {
                                "sort" -> tabState.selectedSort
                                "year" -> tabState.selectedYear
                                "movie-type" -> tabState.selectedMovieType
                                "movie-area" -> tabState.selectedMovieArea
                                "tv-type" -> tabState.selectedTvType
                                "tv-area" -> tabState.selectedTvArea
                                "tv-form" -> tabState.selectedTvForm
                                "show-type" -> tabState.selectedShowType
                                else -> ""
                            }

                            FilterRowWithDialog(
                                options = options,
                                selectedValue = selectedVal,
                                onSelect = onSelect,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { tabState.showFilters = !tabState.showFilters }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixIcon(
                painter = painterResource(if (tabState.showFilters) R.drawable.keyboard_double_arrow_up_outlined else R.drawable.keyboard_double_arrow_down_outlined),
                contentDescription = if (tabState.showFilters) "收起" else "展开",
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            MiuixText(
                text = if (tabState.showFilters) "收起筛选" else "展开筛选",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun FilterableTabContent(
    tab: TabDef,
    filterData: HomeFilterData,
    tabState: FilterableTabState,
    onItemClick: (String) -> Unit,
    onViewImage: (String, String, String) -> Unit,
    onDataLoaded: () -> Unit = {},
    contentBottomPadding: Dp = 0.dp,
) {

    // movie_showing 接口 total 约 50+，count=20 取 3 页兜底；空页即止
    val maxPages = if (tab.useSuggestionApi) 3 else Int.MAX_VALUE

    suspend fun fetchPage(page: Int): List<HomeItem> {
        val pageSize = if (tab.useSuggestionApi) 20 else 32
        val start = page * pageSize
        val url = if (tab.useSuggestionApi) {
            "$DOUBAN_API/subject_collection/movie_showing/items?start=$start&count=$pageSize&apikey=$DOUBAN_APIKEY"
        } else {
            val tagParts = mutableListOf<String>()
            if (tab.fixedTag != null) tagParts.add(tab.fixedTag)
            if (!tabState.selectedYear.contains("不限") && !tabState.selectedYear.contains("全部") && tabState.selectedYear.isNotEmpty()) tagParts.add(
                tabState.selectedYear
            )
            if (tab.categories.contains("movie-type") && !tabState.selectedMovieType.contains("全部") && tabState.selectedMovieType.isNotEmpty()) tagParts.add(
                tabState.selectedMovieType
            )
            if (tab.categories.contains("movie-area") && !tabState.selectedMovieArea.contains("全部") && tabState.selectedMovieArea.isNotEmpty()) tagParts.add(
                tabState.selectedMovieArea
            )
            if (tab.categories.contains("tv-type") && !tabState.selectedTvType.contains("全部") && tabState.selectedTvType.isNotEmpty()) tagParts.add(
                tabState.selectedTvType
            )
            if (tab.categories.contains("tv-area") && !tabState.selectedTvArea.contains("全部") && tabState.selectedTvArea.isNotEmpty()) tagParts.add(
                tabState.selectedTvArea
            )
            if (tab.categories.contains("tv-form") && !tabState.selectedTvForm.contains("全部") && tabState.selectedTvForm.isNotEmpty()) tagParts.add(
                tabState.selectedTvForm
            )
            if (tab.categories.contains("show-type") && !tabState.selectedShowType.contains("全部") && tabState.selectedShowType.isNotEmpty()) tagParts.add(
                tabState.selectedShowType
            )
            val tags = withContext(Dispatchers.IO) {
                URLEncoder.encode(tagParts.joinToString(","), "UTF-8")
            }
            "$DOUBAN_API/${tab.apiType}/recommend?apikey=$DOUBAN_APIKEY&sort=${tabState.selectedSort}&tags=$tags&start=$start&count=32"
        }
        return withContext(Dispatchers.IO) {
            HttpUtil.get(
                url, mapOf(
                    "User-Agent" to AppConfig.UA_WECHAT_PC,
                    "Referer" to "https://servicewechat.com/wx2f9b06c1de1ccfca/99/page-frame.html",
                )
            )
        }.fold(
            onSuccess = { response ->
                try {
                    val json = Json.parseToJsonElement(response).jsonObject
                    if (tab.useSuggestionApi) {
                        // movie_showing：subject_collection_items，cover.url / rating.value / card_subtitle / year
                        json["subject_collection_items"]?.jsonArray?.mapNotNull { element ->
                            val obj = runCatching { element.jsonObject }.getOrNull()
                                ?: return@mapNotNull null
                            val rawCover = (obj["cover"] as? JsonObject)
                                ?.get("url")?.jsonPrimitive?.contentOrNull
                                ?: return@mapNotNull null
                            if (rawCover.isBlank() || rawCover.startsWith("data:")) return@mapNotNull null
                            val cover = if (rawCover.startsWith("http://")) rawCover.replaceFirst(
                                "http://",
                                "https://"
                            ) else rawCover
                            // 部分条目 rating 为 null，无评分则按 0 处理（右上角不显示）
                            val ratingVal = (obj["rating"] as? JsonObject)
                                ?.get("value")?.jsonPrimitive?.doubleOrNull ?: 0.0
                            HomeItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                                cover = cover,
                                coverLarge = cover,
                                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                                subtitle = obj["card_subtitle"]?.jsonPrimitive?.contentOrNull ?: "",
                                rating = ratingVal,
                                year = obj["year"]?.jsonPrimitive?.contentOrNull ?: "",
                            )
                        } ?: emptyList()
                    } else {
                        parseRecommendItems(json)
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            },
            onFailure = { emptyList() },
        )
    }

    val currentFilterKey =
        "${tabState.selectedSort}|${tabState.selectedYear}|${tabState.selectedMovieType}|${tabState.selectedMovieArea}|${tabState.selectedTvType}|${tabState.selectedTvArea}|${tabState.selectedTvForm}|${tabState.selectedShowType}"

    val reloadKey = "$currentFilterKey|${tabState.refreshKey}"

    LaunchedEffect(reloadKey) {
        if (tabState.lastReloadKey == reloadKey && tabState.itemList.isNotEmpty()) {
            return@LaunchedEffect
        }
        tabState.lastReloadKey = reloadKey
        val isRefresh = tabState.prevRefreshKey != tabState.refreshKey
        tabState.prevRefreshKey = tabState.refreshKey
        tabState.loadPage = 0
        tabState.lastLoadPage = 0
        if (!isRefresh) {
            // 筛选切换/切标签：清空旧卡片，走默认全屏转圈；
            // 下拉刷新则保留旧列表，只转顶部/底部
            tabState.itemList = emptyList()
        }
        tabState.isLoading = true
        tabState.isRefreshing = isRefresh
        val result = fetchPage(0)
        tabState.itemList = result
        tabState.hasMore = result.isNotEmpty()
        tabState.isLoading = false
        tabState.isRefreshing = false
        // 筛选变化后数据已换，回顶部（首屏已在顶部，无副作用）
        try {
            tabState.gridState.scrollToItem(0)
        } catch (_: Exception) {
        }
        if (!tabState.firstApiLoaded) {
            tabState.firstApiLoaded = true
            onDataLoaded()
        }
    }

    LaunchedEffect(tabState.loadPage) {
        if (tabState.loadPage == 0 || tabState.lastLoadPage == tabState.loadPage) return@LaunchedEffect
        tabState.lastLoadPage = tabState.loadPage
        tabState.isLoading = true
        val result = fetchPage(tabState.loadPage)
        tabState.itemList += result
        tabState.hasMore = result.isNotEmpty() && tabState.loadPage < maxPages - 1
        tabState.isLoading = false
    }

    fun triggerLoadMore() {
        if (tabState.hasMore && !tabState.isLoading) {
            tabState.loadPage++
        }
    }

    val gridState = tabState.gridState
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 5 && tabState.hasMore && !tabState.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) triggerLoadMore()
    }

    val isAtTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 50
        }
    }
    var lastKnownOffset by remember { mutableIntStateOf(0) }
    var isScrollingDown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            gridState.isScrollInProgress to (gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset)
        }.collect { (isScrolling, state) ->
            if (isScrolling) {
                val (index, offset) = state
                val currentTotal = index * 10000 + offset
                isScrollingDown = currentTotal > lastKnownOffset
                lastKnownOffset = currentTotal
                if (isScrollingDown) {
                    tabState.showFilters = false
                }
            }
        }
    }

    val overscrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && isAtTop && !tabState.showFilters) {
                    tabState.showFilters = true
                }
                return Offset.Zero
            }
        }
    }

    val showFab by remember { derivedStateOf { !isAtTop && !isScrollingDown } }
    val scope = rememberCoroutineScope()

    // 筛选头已外提常驻，这里只负责卡片区：空列表+加载中 => 默认全屏转圈
    // Miuix PullToRefresh 的 content 不是 BoxScope，FAB 叠加在外层 Box 上
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(overscrollConnection),
    ) {
        PullToRefresh(
            isRefreshing = tabState.isRefreshing,
            onRefresh = { tabState.refreshKey++ },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (tabState.isLoading && tabState.itemList.isEmpty()) {
                MiuixLoading(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            } else if (tabState.itemList.isEmpty()) {
                MiuixEmptyText(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp,
                        bottom = 8.dp + contentBottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tabState.itemList, key = { "${it.id}_${it.title}_${it.year}" }) { item ->
                        HomeGridItem(
                            item = item,
                            onClick = { onItemClick(item.title) },
                            onViewImage = { _, _, _ ->
                                onViewImage(
                                    item.coverLarge,
                                    "https://m.douban.com/",
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
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(size = 36.dp)
                            }
                        }
                    }
                }
            }
        }

        if (tabState.itemList.isNotEmpty() && showFab) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        gridState.animateScrollToItem(0); tabState.showFilters = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                MiuixIcon(
                    painter = painterResource(R.drawable.arrow_upward_outlined),
                    contentDescription = "回到顶部",
                    modifier = Modifier.size(28.dp),
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// 非推荐 tab（movie/tv recommend 接口）：items + pic.normal/large + rating.value
private fun parseRecommendItems(json: JsonObject): List<HomeItem> {
    return json["items"]?.jsonArray?.mapNotNull { element ->
        val obj = element.jsonObject
        val picObj = obj["pic"]?.jsonObject
        val rawCover =
            picObj?.get("normal")?.jsonPrimitive?.contentOrNull
                ?: obj["cover"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
        val rawLarge = picObj?.get("large")?.jsonPrimitive?.contentOrNull
            ?: rawCover
        val cover = if (rawCover.startsWith("http://")) rawCover.replaceFirst(
            "http://",
            "https://"
        ) else rawCover
        val coverLarge = if (rawLarge.startsWith("http://")) rawLarge.replaceFirst(
            "http://",
            "https://"
        ) else rawLarge
        val ratingVal =
            obj["rating"]?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull
                ?: obj["rating"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val subtitle = obj["card_subtitle"]?.jsonPrimitive?.contentOrNull ?: ""
        val y = obj["year"]?.jsonPrimitive?.contentOrNull ?: ""
        HomeItem(
            id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
            cover = cover,
            coverLarge = coverLarge,
            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
            subtitle = subtitle,
            rating = ratingVal,
            year = y,
        )
    } ?: emptyList()
}

@SuppressLint("DefaultLocale")
@Composable
private fun HomeGridItem(
    item: HomeItem,
    onClick: () -> Unit,
    onViewImage: (String, String, String) -> Unit
) {
    val slashIndex = item.subtitle.indexOf("/")
    val year = if (slashIndex >= 0) item.subtitle.substring(0, slashIndex).trim() else item.year
    val rest = if (slashIndex >= 0) item.subtitle.substring(slashIndex + 1).trim() else ""

    Card(
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick,
        showIndication = true,
        onLongPress = { onViewImage(item.coverLarge, "https://m.douban.com/", item.title) },
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.cover)
                        .crossfade(true)
                        .httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Referer", "https://m.douban.com/")
                                .build()
                        ).build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp)),
                )

                if (item.rating > 0.0) {
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
                            text = String.format("%.1f", item.rating),
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (rest.isNotEmpty()) {
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
                            text = rest.replace(WHITESPACE_REGEX, "/")
                                .replace(MULTI_SLASH_REGEX, "/"),
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
                if (year.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    MiuixText(
                        text = year,
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
                key(value) {
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
                            .clip(RoundedCornerShape(16.dp))
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
                    key(value) {
                        val isSelected = value == selectedValue
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
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

internal data class HomeFilterData(
    val sortOptions: List<Pair<String, String>>,
    val yearOptions: List<Pair<String, String>>,
    val movieTypeOptions: List<Pair<String, String>>,
    val movieAreaOptions: List<Pair<String, String>>,
    val tvTypeOptions: List<Pair<String, String>>,
    val tvAreaOptions: List<Pair<String, String>>,
    val tvFormOptions: List<Pair<String, String>>,
    val showTypeOptions: List<Pair<String, String>>,
)

private fun parseHomeFilters(text: String): HomeFilterData {
    return try {
        val json = Json.parseToJsonElement(text).jsonObject
        fun parsePairs(arr: JsonArray) = arr.map { e ->
            val o = e.jsonObject
            (o["v"]?.jsonPrimitive?.contentOrNull ?: "") to (o["n"]?.jsonPrimitive?.contentOrNull
                ?: "")
        }
        HomeFilterData(
            sortOptions = parsePairs(json["sort"]?.jsonArray ?: JsonArray(emptyList())),
            yearOptions = parsePairs(json["year"]?.jsonArray ?: JsonArray(emptyList())),
            movieTypeOptions = parsePairs(json["movie-type"]?.jsonArray ?: JsonArray(emptyList())),
            movieAreaOptions = parsePairs(json["movie-area"]?.jsonArray ?: JsonArray(emptyList())),
            tvTypeOptions = parsePairs(json["tv-type"]?.jsonArray ?: JsonArray(emptyList())),
            tvAreaOptions = parsePairs(json["tv-area"]?.jsonArray ?: JsonArray(emptyList())),
            tvFormOptions = parsePairs(json["tv-form"]?.jsonArray ?: JsonArray(emptyList())),
            showTypeOptions = parsePairs(json["show-type"]?.jsonArray ?: JsonArray(emptyList())),
        )
    } catch (_: Exception) {
        HomeFilterData(
            sortOptions = emptyList(),
            yearOptions = emptyList(),
            movieTypeOptions = emptyList(),
            movieAreaOptions = emptyList(),
            tvTypeOptions = emptyList(),
            tvAreaOptions = emptyList(),
            tvFormOptions = emptyList(),
            showTypeOptions = emptyList(),
        )
    }
}

private const val STATE_LOADING = 0
private const val STATE_LOADED = 1
private const val STATE_ERROR = 2

private val WHITESPACE_REGEX = Regex("\\s+")
private val MULTI_SLASH_REGEX = Regex("/+")
