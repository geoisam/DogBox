package pjs.dogbox.ui.screen.discover

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.R
import androidx.compose.material3.CircularProgressIndicator
import pjs.dogbox.ui.components.PagerTabFollowEffect
import pjs.dogbox.ui.navigation.TabStateCache
import pjs.dogbox.utils.HttpUtil
import pjs.dogbox.ui.components.SimpleDragHandle
import java.util.Calendar

private const val API_BASE = "https://zgdypf.zgdypw.cn/getYearData"
private const val MIN_YEAR = 2017

internal data class RankItem(
    val rank: Int,
    val name: String,
    val premiereDate: String,
    val avgPrice: String,
    val salesInWan: String,
)

internal data class BoxOfficeData(
    val title: String,
    val updateTimeDesc: String,
    val rankList: List<RankItem>,
)

internal class BoxOfficeYearState {
    var isLoading by mutableStateOf(true)
    var isRefreshing by mutableStateOf(false)
    var boxOfficeData by mutableStateOf<BoxOfficeData?>(null)
    var refreshKey by mutableIntStateOf(0)
    var prevRefreshKey by mutableIntStateOf(0)
    var lastFetchKey by mutableStateOf("")
    val scrollState = LazyListState()
}

internal class BoxOfficeTotalScreenState {
    val tabCache = TabStateCache { BoxOfficeYearState() }
    var currentYearIndex by mutableIntStateOf(-1)
}

@SuppressLint("DefaultLocale")
private fun formatSales(salesInWan: String): String {
    val value = salesInWan.toDoubleOrNull() ?: return salesInWan
    return if (value > 10000) {
        String.format("%.2f亿", value / 10000)
    } else {
        String.format("%.2f万", value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxOfficeTotalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchMovie: (String) -> Unit = {},
    state: BoxOfficeTotalScreenState = remember { BoxOfficeTotalScreenState() },
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val yearOptions = remember(currentYear) {
        listOf(1 to "全部") + (MIN_YEAR..currentYear).reversed().map { it to "$it" }
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = if (state.currentYearIndex in yearOptions.indices) state.currentYearIndex
        else if (yearOptions.size > 1) 1 else 0,
        pageCount = { yearOptions.size },
    )
    LaunchedEffect(pagerState.currentPage) {
        state.currentYearIndex = pagerState.currentPage
    }
    val tabCache = state.tabCache

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("中国票房", fontWeight = FontWeight.Bold) },
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )

            YearSelector(
                options = yearOptions,
                pagerState = pagerState,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 0,
            ) { page ->
                BoxOfficeYearContent(
                    year = yearOptions[page].first,
                    onSearchMovie = onSearchMovie,
                    tabState = tabCache.get(yearOptions[page].first.toString()),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxOfficeYearContent(
    year: Int,
    onSearchMovie: (String) -> Unit = {},
    tabState: BoxOfficeYearState,
) {
    LaunchedEffect(year, tabState.refreshKey) {
        val fetchKey = "$year|${tabState.refreshKey}"
        if (tabState.lastFetchKey == fetchKey && tabState.boxOfficeData != null) {
            return@LaunchedEffect
        }
        tabState.lastFetchKey = fetchKey
        val isRefresh = tabState.prevRefreshKey != tabState.refreshKey
        tabState.prevRefreshKey = tabState.refreshKey
        tabState.isLoading = true
        tabState.isRefreshing = isRefresh
        val yearParam = if (year == 1) 1 else year
        withContext(Dispatchers.IO) {
            HttpUtil.get("$API_BASE?year=$yearParam")
        }.onSuccess { response ->
            try {
                val json = Json.parseToJsonElement(response).jsonObject
                val title = json["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val updateTimeDesc = json["updateTimeDesc"]?.jsonPrimitive?.contentOrNull ?: ""
                val rankArray = json["rankList"]?.jsonArray ?: JsonArray(emptyList())
                val items = rankArray.mapIndexedNotNull { index, element ->
                    val obj = element.jsonObject
                    if (obj["name"]?.jsonPrimitive?.contentOrNull == null) return@mapIndexedNotNull null
                    RankItem(
                        rank = index + 1,
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "未知影片",
                        premiereDate = obj["premiereDate"]?.jsonPrimitive?.contentOrNull
                            ?: "未知日期",
                        avgPrice = obj["avgPrice"]?.jsonPrimitive?.contentOrNull ?: "0.00",
                        salesInWan = obj["salesInWan"]?.jsonPrimitive?.contentOrNull ?: "<0.1万",
                    )
                }
                tabState.boxOfficeData = BoxOfficeData(
                    title = title,
                    updateTimeDesc = updateTimeDesc,
                    rankList = items,
                )
            } catch (_: Exception) {
            }
        }.onFailure { }
        tabState.isLoading = false
        tabState.isRefreshing = false
    }

    val scrollState = tabState.scrollState

    var showFab by remember { mutableStateOf(false) }
    val fabScope = rememberCoroutineScope()

    LaunchedEffect(scrollState) {
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow {
            scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val scrolledDown = index > prevIndex || (index == prevIndex && offset > prevOffset)
            val pastThreshold = index > 0 || offset > 200
            showFab = !(scrolledDown || !pastThreshold)
            prevIndex = index
            prevOffset = offset
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tabState.isLoading && tabState.boxOfficeData == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
            }
        } else {
            PullToRefreshBox(
                isRefreshing = tabState.isRefreshing,
                onRefresh = { tabState.refreshKey++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                val data = tabState.boxOfficeData
                if (data != null && data.rankList.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = data.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${data.updateTimeDesc} (万元)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 4.dp,
                                bottom = 32.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(data.rankList, key = { it.rank }) { item ->
                                RankListItem(
                                    item = item,
                                    onClick = { onSearchMovie(item.name) },
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (showFab) {
            FloatingActionButton(
                onClick = { fabScope.launch { scrollState.animateScrollToItem(0) } },
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun YearSelector(
    options: List<Pair<Int, String>>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
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
            itemsIndexed(options) { index, (_, label) ->
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
                            onClick = { onSelect(index) },
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
                modifier = Modifier.size(20.dp),
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
                Text(
                    text = "选择年份",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    options.forEachIndexed { index, (_, label) ->
                        val isSelected = index == pagerState.currentPage
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(200),
                        )
                        val txtColor by animateColorAsState(
                            targetValue = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface,
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
                                        onSelect(index)
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

@Composable
private fun RankListItem(item: RankItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${item.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (item.rank - 1) {
                    0 -> Color(0xFFFF3B30)
                    1 -> Color(0xFFFF8A00)
                    2 -> Color(0xFFFFB224)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (item.premiereDate == "1000-01-01") "未知日期 · 均价 ￥${item.avgPrice}" else "${item.premiereDate} · 均价 ￥${item.avgPrice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatSales(item.salesInWan),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}
