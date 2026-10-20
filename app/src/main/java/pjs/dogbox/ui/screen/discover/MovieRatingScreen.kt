package pjs.dogbox.ui.screen.discover

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import pjs.dogbox.data.AppData
import pjs.dogbox.ui.components.AppBottomSheet
import pjs.dogbox.ui.components.CoverImage
import pjs.dogbox.ui.components.MiuixEmptyText
import pjs.dogbox.ui.components.MiuixLoading
import pjs.dogbox.ui.components.MiuixSubScreen
import pjs.dogbox.ui.components.PagerTabFollowEffect
import pjs.dogbox.ui.navigation.TabStateCache
import pjs.dogbox.utils.HttpUtil
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URLEncoder
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFab
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.Text as MiuixText

internal data class MovieItem(
    val id: String,
    val cover: String,
    val coverLarge: String,
    val title: String,
    val subtitle: String,
    val rating: Double,
)

private const val DOUBAN_API = "https://frodo.douban.com/api/v2"
private const val APIKEY = "?apikey=0ac44ae016490db2204ce0a042db2916"

private fun buildUrl(start: Int, category: String, type: String): String {
    val encCate = URLEncoder.encode(category, "UTF-8")
    val encType = URLEncoder.encode(type, "UTF-8")
    return "${DOUBAN_API}/subject/recent_hot/movie${APIKEY}&start=$start&limit=32&category=$encCate&type=$encType"
}

private const val STATE_LOADING = 0
private const val STATE_LOADED = 1
private const val STATE_ERROR = 2

internal class MovieCategoryState {
    var currentPage by mutableIntStateOf(0)
    var movieList by mutableStateOf(emptyList<MovieItem>())
    var isLoading by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var isRefreshing by mutableStateOf(false)
    var refreshKey by mutableIntStateOf(0)
    var prevRefreshKey by mutableIntStateOf(0)
    var lastFetchKey by mutableStateOf("")
    val gridState = LazyGridState()
}

internal class MovieRatingScreenState {
    val filterState = mutableIntStateOf(STATE_LOADING)
    val typeOptions = mutableStateOf<List<Pair<String, String>>>(emptyList())
    val selectedType = mutableStateOf("")
    val tabCache = TabStateCache { MovieCategoryState() }
}

@Composable
internal fun MovieRatingScreen(
    state: MovieRatingScreenState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val tabs = listOf("豆瓣高分", "冷门佳片")
    val categories = listOf("豆瓣高分", "冷门佳片")
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val tabCache = state.tabCache
    var showTabSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var filterState by state.filterState
    var typeOptions by state.typeOptions
    var selectedType by state.selectedType


    LaunchedEffect(Unit) {
        if (filterState != STATE_LOADING) return@LaunchedEffect
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                val text = context.assets.open("json/douban.json")
                    .bufferedReader().use { it.readText() }
                val json = Json.parseToJsonElement(text).jsonObject
                val options = json["hot-type"]?.jsonArray?.mapNotNull { e ->
                    val o = e.jsonObject
                    val v = o["v"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val n = o["n"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    v to n
                } ?: emptyList()
                typeOptions = options
                selectedType = options.firstOrNull()?.first ?: ""
                filterState = STATE_LOADED
                break
            } catch (_: Exception) {
                if (attempts >= 2) filterState = STATE_ERROR
            }
        }
    }

    MiuixSubScreen(
        title = "高分电影",
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
                                        .clip(RoundedCornerShape(50))
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
                                        text = tab,
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

                    // 筛选栏外提：常驻于标签行之下，不随 pager 销毁/重建，
                    // 切换标签时仅标签高亮变化，筛选栏本身不刷新；卡片区各自转圈加载。
                    if (typeOptions.isNotEmpty()) {
                        FilterChipBar(
                            options = typeOptions,
                            selectedValue = selectedType,
                            onValueChange = { newType ->
                                if (newType != selectedType) {
                                    selectedType = newType
                                    // 所有标签页的分页/列表一起重置，切过去时走全屏转圈；
                                    // 回顶部由内容区加载完成后统一 scrollToItem(0)
                                    categories.forEach { cat ->
                                        val s = tabCache.get(cat)
                                        s.currentPage = 0
                                        s.movieList = emptyList()
                                        s.hasMore = true
                                        s.lastFetchKey = ""
                                    }
                                }
                            },
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        beyondViewportPageCount = 0,
                    ) { page ->
                        key(categories[page]) {
                            MovieCategoryContent(
                                category = categories[page],
                                selectedType = selectedType,
                                onSearchMovie = onSearchMovie,
                                onViewImage = onViewImage,
                                tabState = tabCache.get(categories[page]),
                            )
                        }
                    }
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
                tabs.forEachIndexed { index, label ->
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
private fun MovieCategoryContent(
    category: String,
    selectedType: String,
    onSearchMovie: (String) -> Unit = {},
    onViewImage: (String, String, String) -> Unit = { _, _, _ -> },
    tabState: MovieCategoryState,
) {
    LaunchedEffect(tabState.currentPage, selectedType, category, tabState.refreshKey) {
        val fetchKey = "$category|$selectedType|${tabState.currentPage}|${tabState.refreshKey}"
        if (tabState.lastFetchKey == fetchKey && tabState.movieList.isNotEmpty()) {
            return@LaunchedEffect
        }
        tabState.lastFetchKey = fetchKey
        val isRefresh = tabState.prevRefreshKey != tabState.refreshKey
        tabState.prevRefreshKey = tabState.refreshKey
        if (tabState.currentPage == 0 && !isRefresh) {
            // 筛选切换/切标签：清空旧卡片，走默认全屏转圈；下拉刷新则保留旧列表只转顶部
            tabState.movieList = emptyList()
        }
        tabState.isLoading = true
        tabState.isRefreshing = isRefresh
        val start = tabState.currentPage * 32
        val url = buildUrl(start, category, selectedType)
        withContext(Dispatchers.IO) {
            HttpUtil.get(
                url, mapOf(
                    "User-Agent" to AppData.UA_WECHAT_PC,
                    "Referer" to "https://servicewechat.com/wx2f9b06c1de1ccfca/99/page-frame.html",
                )
            )
        }.onSuccess { response ->
            try {
                val json = Json.parseToJsonElement(response).jsonObject
                val items = json["items"]?.jsonArray
                if (items != null) {
                    val parsed = items.mapNotNull { element ->
                        val obj = element.jsonObject
                        val picObj = obj["pic"]?.jsonObject
                        val rawCover =
                            obj["cover"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                                ?: picObj?.get("normal")?.jsonPrimitive?.contentOrNull
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
                        MovieItem(
                            id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                            cover = cover,
                            coverLarge = coverLarge,
                            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                            subtitle = obj["card_subtitle"]?.jsonPrimitive?.contentOrNull ?: "",
                            rating = obj["rating"]?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull
                                ?: 0.0,
                        )
                    }
                    if (tabState.currentPage == 0) tabState.movieList =
                        parsed else tabState.movieList += parsed
                    tabState.hasMore = parsed.size >= 32
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
        if (tabState.currentPage == 0) {
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

    val isAtTop by remember {
        derivedStateOf {
            lazyGridState.firstVisibleItemIndex == 0 && lazyGridState.firstVisibleItemScrollOffset < 50
        }
    }

    val scope = rememberCoroutineScope()

    // 筛选栏已外提常驻，这里只负责卡片区：空列表+加载中 => 默认全屏转圈
    if (tabState.isLoading && tabState.movieList.isEmpty()) {
        MiuixLoading(modifier = Modifier.fillMaxSize())
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            MiuixPullToRefresh(
                isRefreshing = tabState.isRefreshing,
                onRefresh = {
                    tabState.currentPage = 0
                    tabState.refreshKey++
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tabState.movieList.isEmpty() && !tabState.isLoading) {
                    MiuixEmptyText(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = lazyGridState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            tabState.movieList,
                            key = { "${it.id}_${it.title}" }) { item ->
                            MovieGridItem(
                                item = item,
                                onClick = {
                                    if (item.id.isNotEmpty()) {
                                        onSearchMovie(item.title)
                                    }
                                },
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
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MiuixCircularProgressIndicator(size = 36.dp)
                                }
                            }
                        }
                    }
                }
            }

            if (!isAtTop) {
                MiuixFab(
                    onClick = {
                        scope.launch {
                            lazyGridState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    containerColor = MiuixTheme.colorScheme.primaryContainer,
                ) {
                    MiuixIcon(
                        painter = painterResource(R.drawable.arrow_upward_outlined),
                        contentDescription = "回到顶部",
                        modifier = Modifier.size(28.dp),
                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipBar(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = selectedValue == value
                val chipColor by animateColorAsState(
                    targetValue = if (isSelected)
                        MiuixTheme.colorScheme.primary
                    else
                        MiuixTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = tween(200),
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected)
                        MiuixTheme.colorScheme.onPrimary
                    else
                        MiuixTheme.colorScheme.onSurfaceSecondary,
                    animationSpec = tween(200),
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onValueChange(value) },
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

        Spacer(modifier = Modifier.weight(1f))

        MiuixIconButton(
            onClick = { showSheet = true },
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
                    val isSelected = selectedValue == value
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = tween(200),
                    )
                    val txtColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MiuixTheme.colorScheme.onPrimary
                        else
                            MiuixTheme.colorScheme.onSurface,
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
                                    onValueChange(value)
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

@SuppressLint("DefaultLocale")
@Composable
private fun MovieGridItem(
    item: MovieItem,
    onClick: () -> Unit,
    onViewImage: (String, String, String) -> Unit
) {
    val slashIndex = item.subtitle.indexOf("/")
    val year = if (slashIndex >= 0) item.subtitle.substring(0, slashIndex).trim() else ""
    val rest =
        if (slashIndex >= 0) item.subtitle.substring(slashIndex + 1).trim() else item.subtitle

    MiuixCard(
        onClick = onClick,
        onLongPress = { onViewImage(item.coverLarge, "https://m.douban.com/", item.title) },
        colors = MiuixCardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
        ),
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
                                    ),
                                )
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = rest.replace(Regex("\\s+"), "/").replace(Regex("/+"), "/"),
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
