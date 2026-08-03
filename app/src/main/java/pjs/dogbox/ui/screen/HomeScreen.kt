package pjs.dogbox.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
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
import pjs.dogbox.BuildConfig
import pjs.dogbox.R
import pjs.dogbox.util.AppData
import pjs.dogbox.util.OkHttpUtil
import pjs.dogbox.util.SharedData
import pjs.dogbox.util.UpdateBottomSheet
import pjs.dogbox.util.fetchRemoteConfig
import pjs.dogbox.util.parseUpdateInfo
import java.net.URLEncoder

internal data class HomeItem(
    val id: String,
    val cover: String,
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
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {},
) {
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateVer by remember { mutableStateOf("1.0.0") }
    var updateNote by remember { mutableStateOf("修复了一些已知问题") }
    var updateDown by remember { mutableStateOf(AppData.GITHUB_REPO) }
    var dismissedUpdateCode by rememberSaveable { mutableIntStateOf(0) }
    var hasUpdate by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!SharedData.isAutoCheckUpdate(context)) return@LaunchedEffect
        val configJson = fetchRemoteConfig()
        val updateInfo = parseUpdateInfo(configJson)
        if (updateInfo != null) {
            if (updateInfo.code > BuildConfig.VERSION_CODE) {
                hasUpdate = true
                SharedData.setCachedUpdateInfo(
                    SharedData.CachedUpdateInfo(
                        versionCode = updateInfo.code,
                        versionName = updateInfo.ver,
                        note = updateInfo.note,
                        downloadUrl = updateInfo.down,
                    )
                )
                if (updateInfo.code != dismissedUpdateCode) {
                    updateVer = updateInfo.ver
                    updateNote = updateInfo.note
                    updateDown = updateInfo.down
                    showUpdateDialog = true
                }
            } else {
                SharedData.setCachedUpToDate(true)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })


    var showTabSheet by remember { mutableStateOf(false) }
    val tabSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var firstTabLoaded by remember { mutableStateOf(false) }

    Box(modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)) {
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
                                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
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

                    AnimatedVisibility(visible = hasUpdate) {
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, AppData.GITHUB_REPO.toUri())
                                        context.startActivity(intent)
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "有新版本",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                beyondViewportPageCount = 0,
            ) { page ->
                FilterableContent(
                    tab = tabs[page],
                    context = LocalContext.current,
                    onItemClick = { title ->
                        onSearchMovie(title)
                    },
                    onDataLoaded = { firstTabLoaded = true },
                )
            }
        }

        if (showTabSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTabSheet = false },
                sheetState = tabSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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

        UpdateBottomSheet(
            isOpen = showUpdateDialog,
            onDismiss = { showUpdateDialog = false },
            latestVersion = updateVer,
            updateNote = updateNote,
            downloadUrl = updateDown,
        )
    }
}

@Composable
private fun FilterableContent(
    tab: TabDef,
    context: android.content.Context,
    onItemClick: (String) -> Unit,
    onDataLoaded: () -> Unit = {},
) {
    var filterState by remember { mutableIntStateOf(STATE_LOADING) }
    var filterData by remember { mutableStateOf<HomeFilterData?>(null) }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (attempts < 2) {
            attempts++
            val text = withContext(Dispatchers.IO) {
                try {
                    context.assets.open("json/douban.json")
                        .bufferedReader().use { it.readText() }
                } catch (_: Exception) { null }
            }
            if (text != null) {
                filterData = parseHomeFilters(text)
                filterState = STATE_LOADED
                onDataLoaded()
                break
            } else if (attempts >= 2) {
                filterState = STATE_ERROR
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
            filterData?.let { data ->
                FilterableTabContent(
                    tab = tab,
                    filterData = data,
                    onItemClick = onItemClick,
                    onFirstApiLoaded = onDataLoaded,
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun FilterableTabContent(
    tab: TabDef,
    filterData: HomeFilterData,
    onItemClick: (String) -> Unit,
    onFirstApiLoaded: () -> Unit = {},
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

    val initialSort = filterData.sortOptions.firstOrNull()?.first ?: "U"
    var selectedSort by remember { mutableStateOf(initialSort) }

    var selectedYear by remember { mutableStateOf("") }
    var selectedMovieType by remember { mutableStateOf("") }
    var selectedMovieArea by remember { mutableStateOf("") }
    var selectedTvType by remember { mutableStateOf("") }
    var selectedTvArea by remember { mutableStateOf("") }
    var selectedTvForm by remember { mutableStateOf("") }
    var selectedShowType by remember { mutableStateOf("") }

    var itemList by remember { mutableStateOf(emptyList<HomeItem>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var firstApiLoaded by remember { mutableStateOf(false) }

    var loadPage by remember { mutableIntStateOf(0) }
    val prevFilterKey = remember { mutableStateOf("") }

    val maxPages = if (tab.useSuggestionApi) 2 else Int.MAX_VALUE

    suspend fun fetchPage(page: Int): List<HomeItem> {
        val start = page * 32
        val url = if (tab.useSuggestionApi) {
            "$DOUBAN_API/movie/suggestion$APIKEY&start=$start&count=32&new_struct=1&with_review=1"
        } else {
            val tagParts = mutableListOf<String>()
            if (tab.fixedTag != null) tagParts.add(tab.fixedTag)
            if (!selectedYear.contains("不限") && !selectedYear.contains("全部") && selectedYear.isNotEmpty()) tagParts.add(
                selectedYear
            )
            if (tab.categories.contains("movie-type") && !selectedMovieType.contains("全部") && selectedMovieType.isNotEmpty()) tagParts.add(
                selectedMovieType
            )
            if (tab.categories.contains("movie-area") && !selectedMovieArea.contains("全部") && selectedMovieArea.isNotEmpty()) tagParts.add(
                selectedMovieArea
            )
            if (tab.categories.contains("tv-type") && !selectedTvType.contains("全部") && selectedTvType.isNotEmpty()) tagParts.add(
                selectedTvType
            )
            if (tab.categories.contains("tv-area") && !selectedTvArea.contains("全部") && selectedTvArea.isNotEmpty()) tagParts.add(
                selectedTvArea
            )
            if (tab.categories.contains("tv-form") && !selectedTvForm.contains("全部") && selectedTvForm.isNotEmpty()) tagParts.add(
                selectedTvForm
            )
            if (tab.categories.contains("show-type") && !selectedShowType.contains("全部") && selectedShowType.isNotEmpty()) tagParts.add(
                selectedShowType
            )
            val tags = URLEncoder.encode(tagParts.joinToString(","), "UTF-8")
            "$DOUBAN_API/${tab.apiType}/recommend${APIKEY}&sort=$selectedSort&tags=$tags&start=$start&count=32"
        }
        return withContext(Dispatchers.IO) {
            OkHttpUtil.get(
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
                        val rawCover =
                            obj["pic"]?.jsonObject?.get("normal")?.jsonPrimitive?.contentOrNull
                                ?: obj["cover"]?.jsonPrimitive?.contentOrNull
                                ?: return@mapNotNull null
                        val cover = if (rawCover.startsWith("http://")) rawCover.replaceFirst(
                            "http://",
                            "https://"
                        ) else rawCover
                        val ratingVal =
                            obj["rating"]?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull
                                ?: obj["rating"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val subtitle = obj["card_subtitle"]?.jsonPrimitive?.contentOrNull ?: ""
                        val y = obj["year"]?.jsonPrimitive?.contentOrNull ?: ""
                        HomeItem(
                            id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                            cover = cover,
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
        "$selectedSort|$selectedYear|$selectedMovieType|$selectedMovieArea|$selectedTvType|$selectedTvArea|$selectedTvForm|$selectedShowType"

    LaunchedEffect(currentFilterKey) {
        if (currentFilterKey != prevFilterKey.value) {
            prevFilterKey.value = currentFilterKey
            loadPage = 0
        }
        isLoading = true
        itemList = emptyList()
        val result = fetchPage(0)
        itemList = result
        hasMore = result.isNotEmpty() && 0 < maxPages - 1
        isLoading = false
        if (!firstApiLoaded) {
            firstApiLoaded = true
            onFirstApiLoaded()
        }
    }

    LaunchedEffect(loadPage) {
        if (loadPage == 0) return@LaunchedEffect
        isLoading = true
        val result = fetchPage(loadPage)
        itemList += result
        hasMore = result.isNotEmpty() && loadPage < maxPages - 1
        isLoading = false
    }

    fun triggerLoadMore() {
        if (hasMore && !isLoading) {
            loadPage++
        }
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 5 && hasMore && !isLoading
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
    var showFilters by remember { mutableStateOf(true) }
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
                    showFilters = false
                }
            }
        }
    }

    val overscrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && isAtTop && !showFilters) {
                    showFilters = true
                }
                return Offset.Zero
            }
        }
    }

    val showFab by remember { derivedStateOf { !isAtTop && !isScrollingDown } }
    val scope = rememberCoroutineScope()

    val isDataEmpty = itemList.isEmpty()

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)) {
        if (!isDataEmpty) {
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    tab.categories.forEach { category ->
                        val options = categoryFilterMap[category] ?: return@forEach
                        val selectedVal = when (category) {
                            "sort" -> selectedSort
                            "year" -> selectedYear
                            "movie-type" -> selectedMovieType
                            "movie-area" -> selectedMovieArea
                            "tv-type" -> selectedTvType
                            "tv-area" -> selectedTvArea
                            "tv-form" -> selectedTvForm
                            "show-type" -> selectedShowType
                            else -> ""
                        }
                        val onSelect: (String) -> Unit = when (category) {
                            "sort" -> { v -> selectedSort = v }
                            "year" -> { v -> selectedYear = v }
                            "movie-type" -> { v -> selectedMovieType = v }
                            "movie-area" -> { v -> selectedMovieArea = v }
                            "tv-type" -> { v -> selectedTvType = v }
                            "tv-area" -> { v -> selectedTvArea = v }
                            "tv-form" -> { v -> selectedTvForm = v }
                            "show-type" -> { v -> selectedShowType = v }
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
                        .clickable { showFilters = !showFilters }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(if (showFilters) R.drawable.keyboard_double_arrow_up_outlined else R.drawable.keyboard_double_arrow_down_outlined),
                        contentDescription = if (showFilters) "收起" else "展开",
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (showFilters) "收起筛选" else "展开筛选",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .nestedScroll(overscrollConnection)) {
            if (isLoading && itemList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChrysanthemumLoading()
                }
            } else if (itemList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    items(itemList, key = { "${it.id}_${it.title}_${it.year}" }) { item ->
                        HomeGridItem(item = item, onClick = { onItemClick(item.title) })
                    }
                    if (isLoading) {
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
                                gridState.animateScrollToItem(0); showFilters = true
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
private fun HomeGridItem(item: HomeItem, onClick: () -> Unit) {
    val slashIndex = item.subtitle.indexOf("/")
    val year = if (slashIndex >= 0) item.subtitle.substring(0, slashIndex).trim() else item.year
    val rest = if (slashIndex >= 0) item.subtitle.substring(slashIndex + 1).trim() else ""
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
                                .set("Referer", "https://m.douban.com/")
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
                            text = rest.replace(Regex("\\s+"), "/").replace(Regex("/+"), "/"),
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
                        .clip(MaterialTheme.shapes.medium)
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
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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

private data class HomeFilterData(
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
            (o["v"]?.jsonPrimitive?.contentOrNull ?: "") to (o["n"]?.jsonPrimitive?.contentOrNull ?: "")
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