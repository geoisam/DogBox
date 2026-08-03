package pjs.dogbox.ui.screen.discover

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
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.ui.screen.PagerTabFollowEffect
import pjs.dogbox.util.AppData
import pjs.dogbox.util.OkHttpUtil

internal data class AnimeItem(
    val cover: String,
    val title: String,
    val score: Double,
    val isFinish: Int,
    val indexShow: String,
    val order: String,
    val seasonId: Int,
)

private data class FilterData(
    val sortOptions: List<Pair<Int, String>>,
    val orderOptions: List<Pair<Int, String>>,
    val statusOptions: List<Pair<Int, String>>,
    val yearOptions: List<Pair<String, String>>,
    val styleOptions: List<Pair<Int, String>>,
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeRankScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var filterState by remember { mutableIntStateOf(STATE_LOADING) }
    var filterData by remember { mutableStateOf<FilterData?>(null) }
    var isFinish by remember { mutableIntStateOf(-1) }
    var selectedYear by remember { mutableStateOf("-1") }
    var selectedStyleId by remember { mutableIntStateOf(-1) }
    var sortMode by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = { Text("哔哩番剧", fontWeight = FontWeight.Bold) },
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
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )

        when {
            filterState == STATE_LOADING -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ChrysanthemumLoading()
                }
            }

            filterState == STATE_ERROR -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无数据",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                filterData?.let { data ->
                    OrderTabBar(
                        options = data.orderOptions,
                        pagerState = pagerState,
                        onSelect = { value ->
                            val idx = data.orderOptions.indexOfFirst { it.first == value }
                            if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                        },
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                    ) { page ->
                        key(page) {
                            AnimeRankOrderContent(
                                order = data.orderOptions.getOrNull(page)?.first ?: 1,
                                filterData = data,
                                isFinish = isFinish,
                                selectedYear = selectedYear,
                                selectedStyleId = selectedStyleId,
                                sortMode = sortMode,
                                onSortChange = { sortMode = it },
                                onIsFinishChange = { isFinish = it },
                                onYearChange = { selectedYear = it },
                                onStyleIdChange = { selectedStyleId = it },
                                onSearchMovie = onSearchMovie,
                            )
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
    filterData: FilterData,
    isFinish: Int,
    selectedYear: String,
    selectedStyleId: Int,
    sortMode: Int,
    onSortChange: (Int) -> Unit,
    onIsFinishChange: (Int) -> Unit,
    onYearChange: (String) -> Unit,
    onStyleIdChange: (Int) -> Unit,
    onSearchMovie: (String) -> Unit = {},
) {
    var currentPage by remember { mutableIntStateOf(1) }
    var animeList by remember { mutableStateOf(emptyList<AnimeItem>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    LaunchedEffect(order, currentPage, isFinish, selectedYear, selectedStyleId, sortMode) {
        if (currentPage == 1) {
            animeList = emptyList()
        }
        isLoading = true
        val url = buildUrl(
            order = order,
            sort = sortMode,
            page = currentPage,
            isFinish = isFinish,
            year = selectedYear,
            styleId = selectedStyleId,
        )
        withContext(Dispatchers.IO) {
            OkHttpUtil.get(url, mapOf("User-Agent" to AppData.UA_CHROME_PC))
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
                    if (currentPage == 1) animeList = items else animeList += items
                    hasMore = items.size >= 20 && list.size >= 20
                } else {
                    hasMore = false
                }
            } catch (_: Exception) {
                hasMore = false
            }
        }.onFailure {
            hasMore = false
        }
        isLoading = false
    }

    val lazyGridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = lazyGridState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisible = lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 5 && hasMore && !isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoading) {
            currentPage++
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
    var showFilters by remember { mutableStateOf(true) }

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

    val showFab by remember {
        derivedStateOf {
            !isAtTop && !isScrollingDown
        }
    }

    val isDataEmpty = animeList.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isDataEmpty) {
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                FilterBar(
                    filterData = filterData,
                    sortMode = sortMode,
                    isFinish = isFinish,
                    selectedYear = selectedYear,
                    selectedStyleId = selectedStyleId,
                    onSortChange = { onSortChange(it) },
                    onIsFinishChange = { onIsFinishChange(it) },
                    onYearChange = { onYearChange(it) },
                    onStyleIdChange = { onStyleIdChange(it) },
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .nestedScroll(overscrollConnection)) {
            when {
                animeList.isEmpty() && isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChrysanthemumLoading()
                    }
                }

                animeList.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        items(animeList, key = { it.seasonId }) { item ->
                            AnimeGridItem(
                                item = item,
                                onClick = { onSearchMovie(item.title) },
                            )
                        }

                        if (isLoading) {
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
                        }
                    }
                }
            }

            if (showFab) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            lazyGridState.animateScrollToItem(0)
                            showFilters = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_upward_outlined),
                        contentDescription = "回到顶部",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun AnimeGridItem(item: AnimeItem, onClick: () -> Unit) {
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Referer", "https://www.bilibili.com/anime/")
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
                        text = if (item.score > 0.0) String.format("%.1f", item.score)
                        else if (item.isFinish == 1) "已完结" else if (item.isFinish == 0) "连载中" else "0.0",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
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
                        Text(
                            text = item.order,
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
                if (item.indexShow.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.indexShow,
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OrderTabBar(
    options: List<Pair<Int, String>>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                            onClick = { onSelect(value) },
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = { showSheet = true },
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

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
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
                        val isSelected = value == options.getOrNull(pagerState.currentPage)?.first
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
                                        onSelect(value)
                                        showSheet = false
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
        )
        FilterRowWithDialog(
            options = filterData.statusOptions,
            selectedValue = isFinish,
            onSelect = onIsFinishChange,
        )
        FilterRowWithDialog(
            options = filterData.yearOptions,
            selectedValue = selectedYear,
            onSelect = onYearChange,
        )
        FilterRowWithDialog(
            options = filterData.styleOptions,
            selectedValue = selectedStyleId,
            onSelect = onStyleIdChange,
        )
    }
}
