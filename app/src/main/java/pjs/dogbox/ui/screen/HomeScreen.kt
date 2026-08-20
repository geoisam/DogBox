package pjs.dogbox.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.utils.AppData
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.utils.SimpleDragHandle
import java.net.URLEncoder

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
private const val APIKEY = "?apikey=0ac44ae016490db2204ce0a042db2916"

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeScreenState,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
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
    val tabSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var firstTabLoaded by state.firstTabLoaded

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (!firstTabLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChrysanthemumLoading()
            }
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
                                                pagerState.animateScrollToPage(
                                                    index
                                                )
                                            }
                                        },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    FilledTonalIconButton(
                        onClick = { showTabSheet = true },
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
            }

            when (filterState) {
                STATE_LOADING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ChrysanthemumLoading()
                    }
                }

                STATE_ERROR -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                        )
                    }
                }
            }
        }

        if (showTabSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTabSheet = false },
                sheetState = tabSheetState,
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
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = pagerState.currentPage == index
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
                                            scope.launch { pagerState.animateScrollToPage(index) }
                                            showTabSheet = false
                                        },
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = tab.label,
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
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterableTabContent(
    tab: TabDef,
    filterData: HomeFilterData,
    tabState: FilterableTabState,
    onItemClick: (String) -> Unit,
    onViewImage: (String, String, String) -> Unit,
    onDataLoaded: () -> Unit = {},
) {
    val categoryFilterMap = mapOf(
        "sort" to filterData.sortOptions,
        "year" to filterData.yearOptions,
        "movie-type" to filterData.movieTypeOptions,
        "movie-area" to filterData.movieAreaOptions,
        "tv-type" to filterData.tvTypeOptions,
        "tv-area" to filterData.tvAreaOptions,
        "tv-form" to filterData.tvFormOptions,
        "show-type" to filterData.showTypeOptions,
    )

    val maxPages = if (tab.useSuggestionApi) 2 else Int.MAX_VALUE

    suspend fun fetchPage(page: Int): List<HomeItem> {
        val start = page * 32
        val url = if (tab.useSuggestionApi) {
            "$DOUBAN_API/movie/suggestion$APIKEY&start=$start&count=32&new_struct=1&with_review=1"
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
            "$DOUBAN_API/${tab.apiType}/recommend${APIKEY}&sort=${tabState.selectedSort}&tags=$tags&start=$start&count=32"
        }
        return withContext(Dispatchers.IO) {
            HttpUtil.get(
                url, mapOf(
                    "User-Agent" to AppData.UA_WECHAT_PC,
                    "Referer" to "https://servicewechat.com/wx2f9b06c1de1ccfca/99/page-frame.html",
                )
            )
        }.fold(
            onSuccess = { response ->
                try {
                    val json = Json.parseToJsonElement(response).jsonObject
                    json["items"]?.jsonArray?.mapNotNull { element ->
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
        if (!isRefresh) tabState.itemList = emptyList()
        tabState.isLoading = true
        tabState.isRefreshing = isRefresh
        val result = fetchPage(0)
        tabState.itemList = result
        tabState.hasMore = result.isNotEmpty()
        tabState.isLoading = false
        tabState.isRefreshing = false
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

    val isDataEmpty = tabState.itemList.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (!isDataEmpty) {
            AnimatedVisibility(
                visible = tabState.showFilters,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    tab.categories.forEach { category ->
                        val options = categoryFilterMap[category] ?: return@forEach
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
                        val onSelect: (String) -> Unit = when (category) {
                            "sort" -> { v -> tabState.selectedSort = v }
                            "year" -> { v -> tabState.selectedYear = v }
                            "movie-type" -> { v -> tabState.selectedMovieType = v }
                            "movie-area" -> { v -> tabState.selectedMovieArea = v }
                            "tv-type" -> { v -> tabState.selectedTvType = v }
                            "tv-area" -> { v -> tabState.selectedTvArea = v }
                            "tv-form" -> { v -> tabState.selectedTvForm = v }
                            "show-type" -> { v -> tabState.selectedShowType = v }
                            else -> { _ -> }
                        }

                        FilterRowWithDialog(
                            options = options,
                            selectedValue = selectedVal,
                            onSelect = onSelect,
                        )
                    }
                }
            }

            if (tab.categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tabState.showFilters = !tabState.showFilters }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(if (tabState.showFilters) R.drawable.keyboard_double_arrow_up_outlined else R.drawable.keyboard_double_arrow_down_outlined),
                        contentDescription = if (tabState.showFilters) "收起" else "展开",
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (tabState.showFilters) "收起筛选" else "展开筛选",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = tabState.isRefreshing,
            onRefresh = { tabState.refreshKey++ },
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(overscrollConnection),
        ) {
            if (tabState.isLoading && tabState.itemList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChrysanthemumLoading()
                }
            } else if (tabState.itemList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tabState.itemList, key = { "${it.id}_${it.title}_${it.year}" }) { item ->
                        HomeGridItem(
                            item = item,
                            onClick = { onItemClick(item.title) },
                            onViewImage = { _, _, _ -> onViewImage(item.coverLarge, "https://m.douban.com/", item.title) },
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
                                ChrysanthemumLoading(size = 36.dp)
                            }
                        }
                    }
                }

                if (showFab) {
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

@SuppressLint("DefaultLocale")
@Composable
private fun HomeGridItem(item: HomeItem, onClick: () -> Unit, onViewImage: (String, String, String) -> Unit) {
    val slashIndex = item.subtitle.indexOf("/")
    val year = if (slashIndex >= 0) item.subtitle.substring(0, slashIndex).trim() else item.year
    val rest = if (slashIndex >= 0) item.subtitle.substring(slashIndex + 1).trim() else ""

    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { onViewImage(item.coverLarge, "https://m.douban.com/", item.title) },
        ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                        .clip(MaterialTheme.shapes.medium),
                )

                if (item.rating > 0.0) {
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
                            text = String.format("%.1f", item.rating),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
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
                        Text(
                            text = rest.replace(WHITESPACE_REGEX, "/").replace(MULTI_SLASH_REGEX, "/"),
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
                if (year.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = year,
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


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterRowWithDialog(
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = tween(200),
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
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
                            onClick = { onSelect(value) },
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = { showDialog = true },
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
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (showDialog) {
        ModalBottomSheet(
            onDismissRequest = { showDialog = false },
            sheetState = sheetState,
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
                    options.forEach { (value, label) ->
                        val isSelected = value == selectedValue
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(200),
                        )
                        val txtColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            animationSpec = tween(200),
                        )

                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
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
                            Text(
                                text = label,
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