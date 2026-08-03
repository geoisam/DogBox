package pjs.dogbox.ui.screen.movie

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pjs.dogbox.R
import pjs.dogbox.ui.screen.ChrysanthemumLoading
import pjs.dogbox.util.AppData
import pjs.dogbox.util.OkHttpUtil
import java.net.URLEncoder

private data class SearchItem(
    val link: String,
    val cover: String,
    val title: String,
    val note: String,
    val year: String,
    val subtitle: String,
)

private data class PageInfo(
    val hasNext: Boolean,
    val nextUrl: String?,
)

private fun checkPageInfo(doc: Document, currentPage: Int): PageInfo {
    val lastEl = doc.select("#page a[title=尾页]").first()
    if (lastEl != null) {
        val lastHref = lastEl.attr("href")
        val regex = Regex("""(\d+)---/?$""")
        val match = regex.find(lastHref)
        if (match != null) {
            val lastPage = match.groupValues[1].toIntOrNull() ?: 0
            return PageInfo(
                hasNext = currentPage < lastPage,
                nextUrl = null,
            )
        }
    }
    return PageInfo(hasNext = false, nextUrl = null)
}

private fun parseSearchResults(
    html: String,
    currentPage: Int = 1
): Pair<List<SearchItem>, PageInfo> {
    val doc = Ksoup.parse(html)
    val cardElements = doc.select("div.module-card-item")
    val items = cardElements.mapNotNull { card ->
        val linkEl = card.select("a.play-btn").first() ?: return@mapNotNull null
        val imgEl = card.select("img[data-original]").first() ?: return@mapNotNull null
        val titleEl = card.select("div.module-card-item-title").first() ?: return@mapNotNull null

        val linkText = linkEl.attr("href")
        val link = if (linkText.startsWith("http")) linkText else "${AppData.NIVOD_HOST}$linkText"
        val coverText = imgEl.attr("data-original")
        val cover =
            if (coverText.startsWith("http")) coverText else "${AppData.NIVOD_HOST}$coverText"
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
            subtitle = subtitle
        )
    }
    val pageInfo = checkPageInfo(doc, currentPage)
    return items to pageInfo
}

private suspend fun fetchPage(keyword: String, page: Int): Pair<List<SearchItem>, PageInfo> =
    withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "${AppData.NIVOD_SEARCH}$encoded&page=$page"
        OkHttpUtil.get(
            url, mapOf(
                "User-Agent" to AppData.UA_CHROME_PC,
                "Referer" to AppData.NIVOD_HOST,
            )
        ).fold(
            onSuccess = { html -> parseSearchResults(html, page) },
            onFailure = { emptyList<SearchItem>() to PageInfo(hasNext = false, nextUrl = null) },
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieSearchScreen(
    initialQuery: String = "",
    onBack: () -> Unit = {},
    onPlayVideo: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchText by remember { mutableStateOf(initialQuery) }
    var activeQuery by remember { mutableStateOf(initialQuery) }

    var itemList by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }

    LaunchedEffect(activeQuery) {
        if (activeQuery.isNotEmpty()) {
            isLoading = true
            currentPage = 1
            itemList = emptyList()
            val (items, pageInfo) = fetchPage(activeQuery, 1)
            itemList = items
            hasMore = pageInfo.hasNext
            isLoading = false
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage <= 1 || activeQuery.isEmpty()) return@LaunchedEffect
        isLoading = true
        val (items, pageInfo) = fetchPage(activeQuery, currentPage)
        itemList += items
        hasMore = pageInfo.hasNext
        isLoading = false
    }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    fun triggerSearch(query: String) {
        if (query.isNotBlank()) {
            focusManager.clearFocus()
            activeQuery = query
        }
    }

    fun loadMore() {
        if (hasMore && !isLoading) {
            currentPage++
        }
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            if (total == 0) return@snapshotFlow false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 5 && hasMore && !isLoading
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }

    var lastKnownOffset by remember { mutableIntStateOf(0) }
    var isScrollingDown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow {
            gridState.isScrollInProgress to
                    (gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset)
        }.collect { (isScrolling, state) ->
            if (isScrolling) {
                val (index, offset) = state
                val currentTotal = index * 10000 + offset
                isScrollingDown = currentTotal > lastKnownOffset
                lastKnownOffset = currentTotal
            }
        }
    }

    val isAtTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 50
        }
    }

    val showFab by remember {
        derivedStateOf {
            !isAtTop && !isScrollingDown
        }
    }

    val fabScope = rememberCoroutineScope()

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
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            if (isLoading && itemList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChrysanthemumLoading()
                }
            } else if (itemList.isEmpty() && activeQuery.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (itemList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(itemList, key = { it.link }) { item ->
                        SearchGridItem(
                            item = item,
                            onClick = { onPlayVideo(item.link) },
                        )
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
            }

            if (showFab) {
                FloatingActionButton(
                    onClick = { fabScope.launch { gridState.animateScrollToItem(0) } },
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
                                .set("Referer", AppData.NIVOD_HOST)
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
